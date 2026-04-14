import random
import re

from django.conf import settings
from django.core.cache import cache
from django.db import transaction
from django.db.models import Q
from rest_framework import status
from rest_framework.parsers import FormParser, JSONParser, MultiPartParser
from rest_framework.permissions import AllowAny, IsAuthenticated
from rest_framework.response import Response
from rest_framework.views import APIView
from rest_framework_simplejwt.exceptions import TokenError
from rest_framework_simplejwt.tokens import RefreshToken

from apps.accounts.models import User, UserClientLogin
from apps.accounts.tasks import send_otp_task
from apps.identity.serializers import (
    ContactDiscoverySerializer,
    OTPRequestSerializer,
    OTPVerifySerializer,
    ProfileUpdateSerializer,
    PublicUserSerializer,
    TokenRefreshRequestSerializer,
    TokenRevokeSerializer,
    UserSearchQuerySerializer,
    UserSerializer,
    normalize_phone,
)
from apps.spaces.models import Space, SpaceMembership

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
