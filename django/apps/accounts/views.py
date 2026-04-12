import logging
import random
from uuid import UUID

from django.conf import settings
from django.core.cache import cache
from django.db import transaction
from django.db.models import Prefetch, Q
from django.shortcuts import get_object_or_404
from django.utils import timezone
from django.utils.dateparse import parse_date, parse_datetime
from rest_framework import status
from rest_framework.parsers import FormParser, JSONParser, MultiPartParser
from rest_framework.permissions import AllowAny, IsAuthenticated
from rest_framework.response import Response
from rest_framework.views import APIView
from rest_framework_simplejwt.exceptions import TokenError
from rest_framework_simplejwt.tokens import RefreshToken

from .models import Group, GroupMembership, Message, User, UserClientLogin
from .serializers import (
    GroupCreateSerializer,
    GroupMemberAddSerializer,
    GroupMembershipSerializer,
    GroupSerializer,
    GroupMemberUpdateSerializer,
    MessageCreateSerializer,
    MessageSerializer,
    OTPRequestSerializer,
    OTPVerifySerializer,
    TokenRefreshRequestSerializer,
    TokenRevokeSerializer,
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


def create_default_group_for_user(user: User) -> Group:
    group = Group.objects.create(
        title="شخصی",
        description="گروه پیش‌فرض شخصی",
        owner=user,
    )
    GroupMembership.objects.create(
        group=group,
        user=user,
        role=GroupMembership.ROLE_OWNER,
        is_pinned=True,
        can_read_history=True,
    )
    return group


def get_membership(group: Group, user: User) -> GroupMembership | None:
    return group.memberships.filter(user=user).first()


def get_group_for_user_or_404(group_id, user: User) -> Group:
    return get_object_or_404(
        Group.objects.prefetch_related("memberships__user"),
        id=group_id,
        memberships__user=user,
    )


def is_group_admin(group: Group, user: User) -> bool:
    return group.memberships.filter(
        user=user,
        role__in=[GroupMembership.ROLE_OWNER, GroupMembership.ROLE_ADMIN],
        is_active=True,
    ).exists()


def can_manage_members(group: Group, user: User) -> bool:
    return is_group_admin(group, user)


def can_view_all(group: Group, user: User) -> bool:
    return group.memberships.filter(
        user=user,
        role__in=[GroupMembership.ROLE_OWNER, GroupMembership.ROLE_ADMIN, GroupMembership.ROLE_VIEW_ALL],
        is_active=True,
    ).exists()


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
            user, is_new_user = User.objects.get_or_create(phone=phone)
            if is_new_user:
                create_default_group_for_user(user)

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


class GroupListCreateView(APIView):
    permission_classes = [IsAuthenticated]

    def get(self, request):
        groups = (
            Group.objects.filter(
                memberships__user=request.user,
                memberships__can_read_history=True,
                memberships__is_hidden=False,
            )
            .prefetch_related(
                Prefetch("memberships", queryset=GroupMembership.objects.select_related("user").order_by("created_at"))
            )
            .distinct()
            .order_by("-memberships__is_pinned", "-updated_at")
        )
        serializer = GroupSerializer(groups, many=True, context={"request": request})
        return Response({"groups": serializer.data})

    def post(self, request):
        serializer = GroupCreateSerializer(data=request.data)
        serializer.is_valid(raise_exception=True)
        member_ids = serializer.validated_data.pop("member_ids", [])

        with transaction.atomic():
            group = serializer.save(owner=request.user)
            GroupMembership.objects.create(
                group=group,
                user=request.user,
                role=GroupMembership.ROLE_OWNER,
                is_pinned=True,
            )
            if member_ids:
                members = User.objects.filter(id__in=member_ids).exclude(id=request.user.id)
                GroupMembership.objects.bulk_create(
                    [
                        GroupMembership(group=group, user=member, role=GroupMembership.ROLE_MEMBER)
                        for member in members
                    ],
                    ignore_conflicts=True,
                )

        group = Group.objects.prefetch_related("memberships__user").get(id=group.id)
        return Response(
            GroupSerializer(group, context={"request": request}).data,
            status=status.HTTP_201_CREATED,
        )


class GroupDetailView(APIView):
    permission_classes = [IsAuthenticated]

    def get(self, request, group_id):
        group = get_group_for_user_or_404(group_id, request.user)
        serializer = GroupSerializer(group, context={"request": request})
        return Response(serializer.data)

    def patch(self, request, group_id):
        group = get_group_for_user_or_404(group_id, request.user)
        if not is_group_admin(group, request.user):
            return Response(
                {"error": {"code": "FORBIDDEN", "message": "فقط مدیر گروه می‌تواند گروه را ویرایش کند"}},
                status=status.HTTP_403_FORBIDDEN,
            )
        serializer = GroupCreateSerializer(group, data=request.data, partial=True)
        serializer.is_valid(raise_exception=True)
        serializer.save()
        group.refresh_from_db()
        group = Group.objects.prefetch_related("memberships__user").get(id=group.id)
        return Response(GroupSerializer(group, context={"request": request}).data)


class GroupMemberAddView(APIView):
    permission_classes = [IsAuthenticated]

    def post(self, request, group_id):
        group = get_group_for_user_or_404(group_id, request.user)
        if not can_manage_members(group, request.user):
            return Response(
                {"error": {"code": "FORBIDDEN", "message": "فقط مدیر گروه می‌تواند عضو اضافه کند"}},
                status=status.HTTP_403_FORBIDDEN,
            )
        serializer = GroupMemberAddSerializer(data=request.data)
        serializer.is_valid(raise_exception=True)
        member = get_object_or_404(User, id=serializer.validated_data["user_id"])
        membership, created = GroupMembership.objects.update_or_create(
            group=group,
            user=member,
            defaults={
                "role": serializer.validated_data["role"],
                "is_active": True,
                "can_read_history": True,
                "removed_at": None,
            },
        )
        status_code = status.HTTP_201_CREATED if created else status.HTTP_200_OK
        return Response(GroupMembershipSerializer(membership).data, status=status_code)


class GroupMemberListView(APIView):
    permission_classes = [IsAuthenticated]

    def get(self, request, group_id):
        group = get_group_for_user_or_404(group_id, request.user)
        if not can_manage_members(group, request.user):
            return Response(
                {"error": {"code": "FORBIDDEN", "message": "فقط مدیر گروه می‌تواند لیست اعضا را ببیند"}},
                status=status.HTTP_403_FORBIDDEN,
            )
        memberships = group.memberships.select_related("user").order_by("created_at")
        return Response({"members": GroupMembershipSerializer(memberships, many=True).data})


class GroupMemberUpdateView(APIView):
    permission_classes = [IsAuthenticated]

    def patch(self, request, group_id, user_id):
        group = get_group_for_user_or_404(group_id, request.user)
        if not can_manage_members(group, request.user):
            return Response(
                {"error": {"code": "FORBIDDEN", "message": "فقط مدیر گروه می‌تواند نقش اعضا را تغییر دهد"}},
                status=status.HTTP_403_FORBIDDEN,
            )
        membership = get_object_or_404(GroupMembership, group=group, user_id=user_id)
        if membership.role == GroupMembership.ROLE_OWNER:
            return Response(
                {"error": {"code": "FORBIDDEN", "message": "نقش مالک قابل تغییر نیست"}},
                status=status.HTTP_403_FORBIDDEN,
            )
        serializer = GroupMemberUpdateSerializer(data=request.data)
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
        return Response(GroupMembershipSerializer(membership).data)


class GroupMemberRemoveView(APIView):
    permission_classes = [IsAuthenticated]

    def delete(self, request, group_id, user_id):
        group = get_group_for_user_or_404(group_id, request.user)
        if not can_manage_members(group, request.user):
            return Response(
                {"error": {"code": "FORBIDDEN", "message": "فقط مدیر گروه می‌تواند عضو را حذف کند"}},
                status=status.HTTP_403_FORBIDDEN,
            )
        membership = get_object_or_404(GroupMembership, group=group, user_id=user_id)
        if membership.role == GroupMembership.ROLE_OWNER:
            return Response(
                {"error": {"code": "FORBIDDEN", "message": "نقش مالک قابل حذف نیست"}},
                status=status.HTTP_403_FORBIDDEN,
            )
        membership.is_active = False
        membership.removed_at = timezone.now()
        membership.save(update_fields=["is_active", "removed_at"])
        return Response(GroupMembershipSerializer(membership).data)


class GroupPreferenceView(APIView):
    permission_classes = [IsAuthenticated]

    def patch(self, request, group_id):
        group = get_group_for_user_or_404(group_id, request.user)
        membership = get_membership(group, request.user)
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
        return Response(GroupMembershipSerializer(membership).data)


class GroupMessageListCreateView(APIView):
    permission_classes = [IsAuthenticated]
    parser_classes = [JSONParser, MultiPartParser, FormParser]

    def get(self, request, group_id):
        group = get_group_for_user_or_404(group_id, request.user)
        membership = get_membership(group, request.user)
        if not membership or not membership.can_read_history:
            return Response(
                {"error": {"code": "FORBIDDEN", "message": "دسترسی به تاریخچه این گروه ندارید"}},
                status=status.HTTP_403_FORBIDDEN,
            )
        queryset = self._visible_messages_queryset(group, request.user)

        search = request.query_params.get("search", "").strip()
        sender = request.query_params.get("sender")
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

        serializer = MessageSerializer(queryset.distinct(), many=True, context={"request": request})
        return Response({"messages": serializer.data})

    def post(self, request, group_id):
        group = get_group_for_user_or_404(group_id, request.user)
        serializer = MessageCreateSerializer(data=request.data)
        serializer.is_valid(raise_exception=True)
        membership = get_membership(group, request.user)
        if not membership or not membership.is_active:
            return Response(
                {"error": {"code": "FORBIDDEN", "message": "امکان ارسال پیام در این گروه ندارید"}},
                status=status.HTTP_403_FORBIDDEN,
            )

        with transaction.atomic():
            message = serializer.save(group=group, sender=request.user)
            Group.objects.filter(id=group.id).update(updated_at=timezone.now())

        message = Message.objects.select_related("sender").get(id=message.id)
        return Response(
            MessageSerializer(message, context={"request": request}).data,
            status=status.HTTP_201_CREATED,
        )

    def _visible_messages_queryset(self, group: Group, user: User):
        queryset = Message.objects.filter(group=group).select_related("sender")
        if can_view_all(group, user):
            return queryset
        return queryset.filter(Q(sender=user))
