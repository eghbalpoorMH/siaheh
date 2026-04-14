import re

from rest_framework import serializers

from apps.accounts.models import User


class OTPRequestSerializer(serializers.Serializer):
    phone = serializers.RegexField(
        r"^09\d{9}$",
        error_messages={"invalid": "شماره موبایل نامعتبر"},
    )


class OTPVerifySerializer(serializers.Serializer):
    phone = serializers.RegexField(r"^09\d{9}$")
    code = serializers.CharField(max_length=5, min_length=5)
    client_platform = serializers.CharField(max_length=20, required=False, allow_blank=True)
    client_store = serializers.CharField(max_length=30, required=False, allow_blank=True)
    app_version_name = serializers.CharField(max_length=32, required=False, allow_blank=True)
    app_version_code = serializers.IntegerField(required=False, min_value=1)
    device_model = serializers.CharField(max_length=100, required=False, allow_blank=True)
    os_version = serializers.CharField(max_length=50, required=False, allow_blank=True)


class TokenRefreshRequestSerializer(serializers.Serializer):
    refresh_token = serializers.CharField()


class TokenRevokeSerializer(serializers.Serializer):
    refresh_token = serializers.CharField()


class PublicUserSerializer(serializers.ModelSerializer):
    avatar_url = serializers.SerializerMethodField()

    class Meta:
        model = User
        fields = ["id", "username", "display_name", "avatar_url", "about"]

    def get_avatar_url(self, obj):
        if not obj.avatar:
            return None
        request = self.context.get("request")
        if request:
            return request.build_absolute_uri(obj.avatar.url)
        return obj.avatar.url


class UserSerializer(PublicUserSerializer):
    class Meta(PublicUserSerializer.Meta):
        fields = PublicUserSerializer.Meta.fields + ["phone", "created_at"]


class ProfileUpdateSerializer(serializers.ModelSerializer):
    class Meta:
        model = User
        fields = ["display_name", "username", "about", "avatar"]

    def validate_username(self, value):
        value = value.strip().lower()
        if not value:
            raise serializers.ValidationError("نام کاربری نمی‌تواند خالی باشد")
        if len(value) < 4:
            raise serializers.ValidationError("نام کاربری باید حداقل ۴ کاراکتر باشد")
        return value


class UserSearchQuerySerializer(serializers.Serializer):
    q = serializers.CharField(max_length=64)


class ContactDiscoverySerializer(serializers.Serializer):
    phones = serializers.ListField(
        child=serializers.RegexField(r"^09\d{9}$"),
        allow_empty=True,
        required=False,
    )


def normalize_phone(phone: str) -> str:
    digits = re.sub(r"\D", "", phone or "")
    if digits.startswith("98") and len(digits) == 12:
        digits = "0" + digits[2:]
    if len(digits) == 10 and digits.startswith("9"):
        digits = "0" + digits
    return digits
