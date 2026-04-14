import logging
import mimetypes
import random
import re
from uuid import UUID

from django.conf import settings
from django.core.cache import cache
from django.db import transaction
from django.db.models import Prefetch, Q
from django.shortcuts import get_object_or_404
from django.utils import timezone
from django.utils.dateparse import parse_date, parse_datetime
from rest_framework import status
from rest_framework.pagination import PageNumberPagination
from rest_framework.parsers import FormParser, JSONParser, MultiPartParser
from rest_framework.permissions import AllowAny, IsAuthenticated
from rest_framework.response import Response
from rest_framework.views import APIView
from rest_framework_simplejwt.exceptions import TokenError
from rest_framework_simplejwt.tokens import RefreshToken

from .models import Space, SpaceMembership, Message, MessageAttachment, User, UserClientLogin
from .policies import (
    can_manage_members,
    get_space_for_user_or_404,
    get_membership,
    is_space_admin,
    visible_messages_queryset,
)
from .serializers import (
    ContactDiscoverySerializer,
    SpaceCreateSerializer,
    SpaceMemberAddSerializer,
    SpaceMemberUpdateSerializer,
    SpaceMembershipSerializer,
    SpaceSerializer,
    MessageCreateSerializer,
    MessageSerializer,
    OTPRequestSerializer,
    OTPVerifySerializer,
    ProfileUpdateSerializer,
    PublicUserSerializer,
    TokenRefreshRequestSerializer,
    TokenRevokeSerializer,
    UserSearchQuerySerializer,
    UserSerializer,
)
from .tasks import send_otp_task

logger = logging.getLogger(__name__)

OTP_CACHE_PREFIX = "otp:"
OTP_TTL = 120
OTP_COOLDOWN_PREFIX = "otp_cooldown:"
OTP_COOLDOWN = 60
OTP_SEND_RATE_PREFIX = "otp_send_rate:"
OTP_SEND_RATE_LIMIT = 3
OTP_SEND_RATE_WINDOW = 600
OTP_VERIFY_ATTEMPTS_PREFIX = "otp_verify_attempts:"
OTP_MAX_VERIFY_ATTEMPTS = 5


def generate_unique_username() -> str:
    while True:
        candidate = f"user{random.randint(100000, 999999)}"
        if not User.objects.filter(username=candidate).exists():
            return candidate


def create_default_space_for_user(user: User) -> Space:
    space = Space.objects.create(
        title="شخصی",
        description="فضا پیش‌فرض شخصی",
        owner=user,
        kind=Space.KIND_PERSONAL,
    )
    SpaceMembership.objects.create(
        space=space,
        user=user,
        role=SpaceMembership.ROLE_OWNER,
        is_pinned=True,
        can_read_history=True,
    )
    return space


def detect_attachment_kind(content_type: str | None, file_name: str, declared_kind: str | None = None) -> str:
    if declared_kind in {
        MessageAttachment.KIND_IMAGE,
        MessageAttachment.KIND_VIDEO,
        MessageAttachment.KIND_MUSIC,
        MessageAttachment.KIND_VOICE,
        MessageAttachment.KIND_DOCUMENT,
        MessageAttachment.KIND_FILE,
    }:
        return declared_kind
    mime = content_type or mimetypes.guess_type(file_name)[0] or ""
    if mime.startswith("image/"):
        return MessageAttachment.KIND_IMAGE
    if mime.startswith("video/"):
        return MessageAttachment.KIND_VIDEO
    if mime.startswith("audio/"):
        return MessageAttachment.KIND_MUSIC
    if mime in {"application/pdf"} or mime.startswith("text/"):
        return MessageAttachment.KIND_DOCUMENT
    return MessageAttachment.KIND_FILE


def normalize_phone(phone: str) -> str:
    digits = re.sub(r"\D", "", phone or "")
    if digits.startswith("98") and len(digits) == 12:
        digits = "0" + digits[2:]
    if len(digits) == 10 and digits.startswith("9"):
        digits = "0" + digits
    return digits


class EntryPagination(PageNumberPagination):
    page_size = 40
    page_size_query_param = "page_size"
    max_page_size = 100


class OTPRequestView(APIView):
    permission_classes = [AllowAny]

    def post(self, request):
        serializer = OTPRequestSerializer(data=request.data)
        serializer.is_valid(raise_exception=True)
        phone = serializer.validated_data["phone"]

        cooldown_key = f"{OTP_COOLDOWN_PREFIX}{phone}"
        if cache.get(cooldown_key):
            return Response(
                {
                    "error": {
                        "code": "TOO_MANY_REQUESTS",
                        "message": "لطفاً کمی صبر کنید و دوباره تلاش کنید",
                        "retry_after": OTP_COOLDOWN,
                    }
                },
                status=status.HTTP_429_TOO_MANY_REQUESTS,
            )

        rate_key = f"{OTP_SEND_RATE_PREFIX}{phone}"
        if not cache.add(rate_key, 1, timeout=OTP_SEND_RATE_WINDOW):
            send_count = cache.incr(rate_key)
            if send_count > OTP_SEND_RATE_LIMIT:
                return Response(
                    {
                        "error": {
                            "code": "TOO_MANY_REQUESTS",
                            "message": "تعداد درخواست‌های شما بیش از حد مجاز است. لطفاً ۱۰ دقیقه دیگر تلاش کنید",
                            "retry_after": OTP_SEND_RATE_WINDOW,
                        }
                    },
                    status=status.HTTP_429_TOO_MANY_REQUESTS,
                )

        code = f"{random.randint(10000, 99999)}"
        cache.set(f"{OTP_CACHE_PREFIX}{phone}", code, timeout=OTP_TTL)
        cache.set(cooldown_key, True, timeout=OTP_COOLDOWN)
        send_otp_task.delay(phone, code)
        return Response({"expires_in": OTP_TTL}, status=status.HTTP_201_CREATED)


class TokenView(APIView):
    def get_permissions(self):
        if self.request.method == "DELETE":
            return [IsAuthenticated()]
        return [AllowAny()]

    def post(self, request):
        serializer = OTPVerifySerializer(data=request.data)
        serializer.is_valid(raise_exception=True)

        phone = serializer.validated_data["phone"]
        code = serializer.validated_data["code"]
        cache_key = f"{OTP_CACHE_PREFIX}{phone}"
        attempts_key = f"{OTP_VERIFY_ATTEMPTS_PREFIX}{phone}"

        attempts = cache.get(attempts_key, 0)
        if attempts >= OTP_MAX_VERIFY_ATTEMPTS:
            cache.delete(cache_key)
            cache.delete(attempts_key)
            return Response(
                {
                    "error": {
                        "code": "TOO_MANY_ATTEMPTS",
                        "message": "تعداد تلاش‌های ناموفق بیش از حد مجاز است. لطفاً مجدداً درخواست کد دهید",
                    }
                },
                status=status.HTTP_429_TOO_MANY_REQUESTS,
            )

        stored_code = cache.get(cache_key)
        if stored_code is None or stored_code != code:
            if not cache.add(attempts_key, 1, timeout=OTP_TTL):
                cache.incr(attempts_key)
            return Response(
                {"error": {"code": "INVALID_OTP", "message": "کد تأیید نامعتبر یا منقضی شده"}},
                status=status.HTTP_401_UNAUTHORIZED,
            )

        cache.delete(cache_key)
        cache.delete(attempts_key)

        with transaction.atomic():
            defaults = {"username": generate_unique_username(), "display_name": ""}
            user, is_new_user = User.objects.get_or_create(phone=phone, defaults=defaults)
            if is_new_user:
                create_default_space_for_user(user)
            elif not user.username:
                user.username = generate_unique_username()
                user.save(update_fields=["username"])

            UserClientLogin.objects.create(
                user=user,
                platform=serializer.validated_data.get("client_platform") or UserClientLogin.PLATFORM_ANDROID,
                store=serializer.validated_data.get("client_store") or "unknown",
                app_version_name=serializer.validated_data.get("app_version_name") or "",
                app_version_code=serializer.validated_data.get("app_version_code"),
                device_model=serializer.validated_data.get("device_model") or "",
                os_version=serializer.validated_data.get("os_version") or "",
                ip_address=self._get_client_ip(request),
                user_agent=request.headers.get("User-Agent", ""),
            )

        refresh = RefreshToken.for_user(user)
        return Response(
            {
                "access_token": str(refresh.access_token),
                "refresh_token": str(refresh),
                "is_new_user": is_new_user,
                "update": {
                    "status": "none",
                    "message": "",
                    "store": serializer.validated_data.get("client_store") or "",
                    "min_version": settings.APP_VERSION_MIN,
                    "latest_version": settings.APP_VERSION_LATEST,
                    "update_url": settings.APP_UPDATE_URL,
                },
                "user": UserSerializer(user, context={"request": request}).data,
            }
        )

    def put(self, request):
        serializer = TokenRefreshRequestSerializer(data=request.data)
        serializer.is_valid(raise_exception=True)
        try:
            refresh = RefreshToken(serializer.validated_data["refresh_token"])
            return Response(
                {
                    "access_token": str(refresh.access_token),
                    "refresh_token": str(refresh),
                }
            )
        except TokenError:
            return Response(
                {"error": {"code": "INVALID_REFRESH_TOKEN", "message": "رفرش‌توکن نامعتبر است"}},
                status=status.HTTP_401_UNAUTHORIZED,
            )

    def delete(self, request):
        serializer = TokenRevokeSerializer(data=request.data)
        serializer.is_valid(raise_exception=True)
        try:
            refresh = RefreshToken(serializer.validated_data["refresh_token"])
            refresh.blacklist()
        except TokenError:
            pass
        return Response(status=status.HTTP_204_NO_CONTENT)

    def _get_client_ip(self, request):
        forwarded_for = request.META.get("HTTP_X_FORWARDED_FOR")
        if forwarded_for:
            return forwarded_for.split(",")[0].strip()
        return request.META.get("REMOTE_ADDR")


class MeView(APIView):
    permission_classes = [IsAuthenticated]

    def get(self, request):
        return Response(UserSerializer(request.user, context={"request": request}).data)


class ProfileView(APIView):
    permission_classes = [IsAuthenticated]
    parser_classes = [JSONParser, MultiPartParser, FormParser]

    def patch(self, request):
        serializer = ProfileUpdateSerializer(request.user, data=request.data, partial=True)
        serializer.is_valid(raise_exception=True)
        serializer.save()
        return Response(UserSerializer(request.user, context={"request": request}).data)


class UserSearchView(APIView):
    permission_classes = [IsAuthenticated]

    def get(self, request):
        serializer = UserSearchQuerySerializer(data=request.query_params)
        serializer.is_valid(raise_exception=True)
        query = serializer.validated_data["q"].strip().lower()
        users = User.objects.filter(
            Q(username__istartswith=query) | Q(display_name__icontains=query)
        ).exclude(id=request.user.id)[:20]
        return Response({"users": PublicUserSerializer(users, many=True, context={"request": request}).data})


class ContactDiscoveryView(APIView):
    permission_classes = [IsAuthenticated]

    def post(self, request):
        serializer = ContactDiscoverySerializer(data=request.data)
        serializer.is_valid(raise_exception=True)
        phones = serializer.validated_data.get("phones", [])
        normalized = [normalize_phone(p) for p in phones]
        normalized = [p for p in normalized if re.match(r"^09\d{9}$", p)]
        users = User.objects.filter(phone__in=normalized).exclude(id=request.user.id)[:200]
        return Response({"users": PublicUserSerializer(users, many=True, context={"request": request}).data})


class SpaceListCreateView(APIView):
    permission_classes = [IsAuthenticated]

    def get(self, request):
        spaces = (
            Space.objects.filter(
                memberships__user=request.user,
                memberships__can_read_history=True,
                memberships__is_hidden=False,
            )
            .prefetch_related(
                Prefetch("memberships", queryset=SpaceMembership.objects.select_related("user").order_by("created_at"))
            )
            .distinct()
            .order_by("-memberships__is_pinned", "-updated_at")
        )
        serializer = SpaceSerializer(spaces, many=True, context={"request": request})
        return Response({"spaces": serializer.data})

    def post(self, request):
        serializer = SpaceCreateSerializer(data=request.data)
        serializer.is_valid(raise_exception=True)
        member_ids = serializer.validated_data.pop("member_ids", [])

        with transaction.atomic():
            space = serializer.save(owner=request.user, kind=Space.KIND_SPACE)
            SpaceMembership.objects.create(
                space=space,
                user=request.user,
                role=SpaceMembership.ROLE_OWNER,
                is_pinned=True,
            )
            if member_ids:
                members = User.objects.filter(id__in=member_ids).exclude(id=request.user.id)
                SpaceMembership.objects.bulk_create(
                    [
                        SpaceMembership(space=space, user=member, role=SpaceMembership.ROLE_MEMBER)
                        for member in members
                    ],
                    ignore_conflicts=True,
                )

        space = Space.objects.prefetch_related("memberships__user").get(id=space.id)
        return Response(
            SpaceSerializer(space, context={"request": request}).data,
            status=status.HTTP_201_CREATED,
        )


class SpaceDetailView(APIView):
    permission_classes = [IsAuthenticated]

    def get(self, request, space_id):
        space = get_space_for_user_or_404(space_id, request.user)
        serializer = SpaceSerializer(space, context={"request": request})
        return Response(serializer.data)

    def patch(self, request, space_id):
        space = get_space_for_user_or_404(space_id, request.user)
        if not is_space_admin(space, request.user):
            return Response(
                {"error": {"code": "FORBIDDEN", "message": "فقط مدیر فضا می‌تواند فضا را ویرایش کند"}},
                status=status.HTTP_403_FORBIDDEN,
            )
        serializer = SpaceCreateSerializer(space, data=request.data, partial=True)
        serializer.is_valid(raise_exception=True)
        serializer.save()
        space.refresh_from_db()
        space = Space.objects.prefetch_related("memberships__user").get(id=space.id)
        return Response(SpaceSerializer(space, context={"request": request}).data)


class SpaceConvertView(APIView):
    permission_classes = [IsAuthenticated]

    def post(self, request, space_id):
        space = get_space_for_user_or_404(space_id, request.user)
        if space.owner_id != request.user.id:
            return Response(
                {"error": {"code": "FORBIDDEN", "message": "فقط مالک می‌تواند فضا شخصی را تبدیل کند"}},
                status=status.HTTP_403_FORBIDDEN,
            )
        if space.kind != Space.KIND_PERSONAL:
            return Response({"error": {"code": "INVALID_STATE", "message": "این فضا شخصی نیست"}}, status=400)
        if request.data.get("confirm") is not True:
            return Response(
                {"error": {"code": "CONFIRM_REQUIRED", "message": "برای تبدیل باید confirm=true ارسال شود"}},
                status=400,
            )
        space.kind = Space.KIND_SPACE
        space.save(update_fields=["kind"])
        return Response(SpaceSerializer(space, context={"request": request}).data)


class SpaceMemberAddView(APIView):
    permission_classes = [IsAuthenticated]

    def post(self, request, space_id):
        space = get_space_for_user_or_404(space_id, request.user)
        if not can_manage_members(space, request.user):
            return Response(
                {"error": {"code": "FORBIDDEN", "message": "فقط مدیر فضا می‌تواند عضو اضافه کند"}},
                status=status.HTTP_403_FORBIDDEN,
            )
        serializer = SpaceMemberAddSerializer(data=request.data)
        serializer.is_valid(raise_exception=True)
        user_id = serializer.validated_data.get("user_id")
        username = serializer.validated_data.get("username")
        if user_id:
            member = get_object_or_404(User, id=user_id)
        else:
            member = get_object_or_404(User, username=username.lower())
        membership, created = SpaceMembership.objects.update_or_create(
            space=space,
            user=member,
            defaults={
                "role": serializer.validated_data["role"],
                "is_active": True,
                "can_read_history": True,
                "removed_at": None,
            },
        )
        status_code = status.HTTP_201_CREATED if created else status.HTTP_200_OK
        return Response(SpaceMembershipSerializer(membership, context={"request": request}).data, status=status_code)


class SpaceMemberListView(APIView):
    permission_classes = [IsAuthenticated]

    def get(self, request, space_id):
        space = get_space_for_user_or_404(space_id, request.user)
        if not can_manage_members(space, request.user):
            return Response(
                {"error": {"code": "FORBIDDEN", "message": "فقط مدیر فضا می‌تواند لیست اعضا را ببیند"}},
                status=status.HTTP_403_FORBIDDEN,
            )
        memberships = space.memberships.select_related("user").order_by("created_at")
        return Response({"members": SpaceMembershipSerializer(memberships, many=True, context={"request": request}).data})


class SpaceMemberUpdateView(APIView):
    permission_classes = [IsAuthenticated]

    def patch(self, request, space_id, user_id):
        space = get_space_for_user_or_404(space_id, request.user)
        if not can_manage_members(space, request.user):
            return Response(
                {"error": {"code": "FORBIDDEN", "message": "فقط مدیر فضا می‌تواند نقش اعضا را تغییر دهد"}},
                status=status.HTTP_403_FORBIDDEN,
            )
        membership = get_object_or_404(SpaceMembership, space=space, user_id=user_id)
        if membership.role == SpaceMembership.ROLE_OWNER:
            return Response(
                {"error": {"code": "FORBIDDEN", "message": "نقش مالک قابل تغییر نیست"}},
                status=status.HTTP_403_FORBIDDEN,
            )
        serializer = SpaceMemberUpdateSerializer(data=request.data)
        serializer.is_valid(raise_exception=True)
        updates = []
        if "role" in serializer.validated_data:
            membership.role = serializer.validated_data["role"]
            updates.append("role")
        if "can_read_history" in serializer.validated_data:
            membership.can_read_history = serializer.validated_data["can_read_history"]
            updates.append("can_read_history")
        if "is_active" in serializer.validated_data:
            membership.is_active = serializer.validated_data["is_active"]
            updates.append("is_active")
            membership.removed_at = timezone.now() if not membership.is_active else None
            updates.append("removed_at")
        if updates:
            membership.save(update_fields=updates)
        return Response(SpaceMembershipSerializer(membership, context={"request": request}).data)


class SpaceMemberRemoveView(APIView):
    permission_classes = [IsAuthenticated]

    def delete(self, request, space_id, user_id):
        space = get_space_for_user_or_404(space_id, request.user)
        if not can_manage_members(space, request.user):
            return Response(
                {"error": {"code": "FORBIDDEN", "message": "فقط مدیر فضا می‌تواند عضو را حذف کند"}},
                status=status.HTTP_403_FORBIDDEN,
            )
        membership = get_object_or_404(SpaceMembership, space=space, user_id=user_id)
        if membership.role == SpaceMembership.ROLE_OWNER:
            return Response(
                {"error": {"code": "FORBIDDEN", "message": "نقش مالک قابل حذف نیست"}},
                status=status.HTTP_403_FORBIDDEN,
            )
        membership.is_active = False
        membership.removed_at = timezone.now()
        membership.save(update_fields=["is_active", "removed_at"])
        return Response(SpaceMembershipSerializer(membership, context={"request": request}).data)


class SpacePreferenceView(APIView):
    permission_classes = [IsAuthenticated]

    def patch(self, request, space_id):
        space = get_space_for_user_or_404(space_id, request.user)
        membership = get_membership(space, request.user)
        if not membership:
            return Response(
                {"error": {"code": "NOT_FOUND", "message": "عضویت یافت نشد"}},
                status=status.HTTP_404_NOT_FOUND,
            )
        is_pinned = request.data.get("is_pinned")
        is_hidden = request.data.get("is_hidden")
        updates = []
        if isinstance(is_pinned, bool):
            membership.is_pinned = is_pinned
            updates.append("is_pinned")
        if isinstance(is_hidden, bool):
            membership.is_hidden = is_hidden
            updates.append("is_hidden")
        if updates:
            membership.save(update_fields=updates)
        return Response(SpaceMembershipSerializer(membership, context={"request": request}).data)


class SpaceMessageListCreateView(APIView):
    permission_classes = [IsAuthenticated]
    parser_classes = [JSONParser, MultiPartParser, FormParser]

    def get(self, request, space_id):
        space = get_space_for_user_or_404(space_id, request.user)
        membership = get_membership(space, request.user)
        if not membership or not membership.can_read_history:
            return Response(
                {"error": {"code": "FORBIDDEN", "message": "دسترسی به تاریخچه این فضا ندارید"}},
                status=status.HTTP_403_FORBIDDEN,
            )
        queryset = visible_messages_queryset(space, request.user)

        search = request.query_params.get("search", "").strip()
        sender = request.query_params.get("sender")
        sender_username = request.query_params.get("sender_username")
        date_from = request.query_params.get("date_from")
        date_to = request.query_params.get("date_to")

        if search:
            queryset = queryset.filter(text__icontains=search)
        if sender:
            try:
                queryset = queryset.filter(sender_id=UUID(sender))
            except ValueError:
                return Response(
                    {"error": {"code": "INVALID_SENDER", "message": "شناسه فرستنده نامعتبر است"}},
                    status=status.HTTP_400_BAD_REQUEST,
                )
        if sender_username:
            queryset = queryset.filter(sender__username__iexact=sender_username.strip().lower())
        if date_from:
            dt = parse_datetime(date_from) or None
            if dt is None:
                d = parse_date(date_from)
                if d is None:
                    return Response(
                        {"error": {"code": "INVALID_DATE", "message": "فرمت تاریخ شروع نامعتبر است"}},
                        status=status.HTTP_400_BAD_REQUEST,
                    )
                dt = timezone.make_aware(timezone.datetime.combine(d, timezone.datetime.min.time()))
            queryset = queryset.filter(created_at__gte=dt)
        if date_to:
            dt = parse_datetime(date_to) or None
            if dt is None:
                d = parse_date(date_to)
                if d is None:
                    return Response(
                        {"error": {"code": "INVALID_DATE", "message": "فرمت تاریخ پایان نامعتبر است"}},
                        status=status.HTTP_400_BAD_REQUEST,
                    )
                dt = timezone.make_aware(timezone.datetime.combine(d, timezone.datetime.max.time()))
            queryset = queryset.filter(created_at__lte=dt)

        paginator = EntryPagination()
        page = paginator.paginate_queryset(queryset.distinct(), request)
        serializer = MessageSerializer(page, many=True, context={"request": request})
        return paginator.get_paginated_response({"entries": serializer.data})

    def post(self, request, space_id):
        space = get_space_for_user_or_404(space_id, request.user)
        serializer = MessageCreateSerializer(data=request.data)
        serializer.is_valid(raise_exception=True)
        membership = get_membership(space, request.user)
        if not membership or not membership.is_active:
            return Response(
                {"error": {"code": "FORBIDDEN", "message": "امکان ثبت رویداد در این فضا ندارید"}},
                status=status.HTTP_403_FORBIDDEN,
            )

        attachments = serializer.validated_data.pop("attachments", [])
        attachment_kinds = serializer.validated_data.pop("attachment_kinds", [])
        with transaction.atomic():
            message = Message.objects.create(space=space, sender=request.user, text=serializer.validated_data.get("text", ""))
            files = list(attachments)
            for index, f in enumerate(files):
                declared_kind = attachment_kinds[index] if index < len(attachment_kinds) else None
                MessageAttachment.objects.create(
                    message=message,
                    file=f,
                    kind=detect_attachment_kind(
                        getattr(f, "content_type", None),
                        getattr(f, "name", ""),
                        declared_kind=declared_kind,
                    ),
                    original_name=getattr(f, "name", "") or "",
                    sort_order=index,
                )
            Space.objects.filter(id=space.id).update(updated_at=timezone.now())

        message = Message.objects.select_related("sender").prefetch_related("attachments").get(id=message.id)
        return Response(
            MessageSerializer(message, context={"request": request}).data,
            status=status.HTTP_201_CREATED,
        )
