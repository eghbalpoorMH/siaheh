from rest_framework import serializers
from django.conf import settings

from .models import Space, SpaceMembership, Message, MessageAttachment, User


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


class SpaceMembershipSerializer(serializers.ModelSerializer):
    user = PublicUserSerializer(read_only=True)

    class Meta:
        model = SpaceMembership
        fields = [
            "id",
            "user_id",
            "user",
            "role",
            "is_active",
            "can_read_history",
            "is_pinned",
            "is_hidden",
            "created_at",
            "removed_at",
        ]


class SpaceSerializer(serializers.ModelSerializer):
    members_count = serializers.SerializerMethodField()
    role = serializers.SerializerMethodField()
    is_pinned = serializers.SerializerMethodField()
    is_hidden = serializers.SerializerMethodField()
    latest_entry_preview = serializers.SerializerMethodField()
    members = SpaceMembershipSerializer(source="memberships", many=True, read_only=True)

    class Meta:
        model = Space
        fields = [
            "id",
            "title",
            "description",
            "kind",
            "members_count",
            "role",
            "is_pinned",
            "is_hidden",
            "latest_entry_preview",
            "members",
            "created_at",
            "updated_at",
        ]
        read_only_fields = ["id", "members_count", "role", "is_pinned", "is_hidden", "latest_entry_preview", "members", "kind"]

    def get_members_count(self, obj):
        return obj.memberships.filter(is_active=True).count()

    def _get_membership(self, obj):
        request = self.context.get("request")
        user = getattr(request, "user", None)
        if not user or not user.is_authenticated:
            return None
        return obj.memberships.filter(user=user).first()

    def get_role(self, obj):
        membership = self._get_membership(obj)
        return membership.role if membership else None

    def get_is_pinned(self, obj):
        membership = self._get_membership(obj)
        return bool(membership and membership.is_pinned)

    def get_is_hidden(self, obj):
        membership = self._get_membership(obj)
        return bool(membership and membership.is_hidden)

    def get_latest_entry_preview(self, obj):
        last = obj.messages.order_by("-created_at").first()
        if not last:
            return ""
        if last.text:
            return last.text[:80]
        if last.attachments.exists():
            return "فایل ضمیمه"
        return ""


class SpaceCreateSerializer(serializers.ModelSerializer):
    member_ids = serializers.ListField(
        child=serializers.UUIDField(),
        required=False,
        allow_empty=True,
        write_only=True,
    )

    class Meta:
        model = Space
        fields = ["id", "title", "description", "member_ids", "created_at", "updated_at"]
        read_only_fields = ["id", "created_at", "updated_at"]


class SpaceMemberAddSerializer(serializers.Serializer):
    user_id = serializers.UUIDField(required=False)
    username = serializers.CharField(max_length=32, required=False, allow_blank=False)
    role = serializers.ChoiceField(choices=SpaceMembership.ROLE_CHOICES, default=SpaceMembership.ROLE_MEMBER)

    def validate(self, attrs):
        if not attrs.get("user_id") and not attrs.get("username"):
            raise serializers.ValidationError("شناسه کاربر یا نام کاربری الزامی است")
        return attrs


class SpaceMemberUpdateSerializer(serializers.Serializer):
    role = serializers.ChoiceField(choices=SpaceMembership.ROLE_CHOICES, required=False)
    can_read_history = serializers.BooleanField(required=False)
    is_active = serializers.BooleanField(required=False)


class MessageAttachmentSerializer(serializers.ModelSerializer):
    file_url = serializers.SerializerMethodField()

    class Meta:
        model = MessageAttachment
        fields = [
            "id",
            "kind",
            "original_name",
            "sort_order",
            "file_url",
        ]

    def get_file_url(self, obj):
        request = self.context.get("request")
        if request:
            return request.build_absolute_uri(obj.file.url)
        return obj.file.url


class MessageSerializer(serializers.ModelSerializer):
    sender = PublicUserSerializer(read_only=True)
    attachments = MessageAttachmentSerializer(many=True, read_only=True)

    class Meta:
        model = Message
        fields = [
            "id",
            "space_id",
            "sender_id",
            "sender",
            "text",
            "attachments",
            "created_at",
            "updated_at",
        ]
        read_only_fields = ["id", "sender_id", "sender", "attachments"]


class MessageCreateSerializer(serializers.ModelSerializer):
    attachments = serializers.ListField(
        child=serializers.FileField(),
        required=False,
        allow_empty=True,
        write_only=True,
    )
    attachment_kinds = serializers.ListField(
        child=serializers.ChoiceField(choices=MessageAttachment.KIND_CHOICES),
        required=False,
        allow_empty=True,
        write_only=True,
    )

    class Meta:
        model = Message
        fields = ["id", "text", "attachments", "attachment_kinds", "created_at", "updated_at"]
        read_only_fields = ["id", "created_at", "updated_at"]

    def validate(self, attrs):
        text = (attrs.get("text") or "").strip()
        files = attrs.get("attachments") or []
        attachment_kinds = attrs.get("attachment_kinds") or []
        if not text and not files:
            raise serializers.ValidationError("ثبت باید متن یا فایل داشته باشد")
        if attachment_kinds and len(attachment_kinds) != len(files):
            raise serializers.ValidationError("تعداد نوع فایل‌ها باید با تعداد فایل‌ها برابر باشد")
        max_attachments = max(getattr(settings, "ENTRY_MAX_ATTACHMENTS", 10), 1)
        if len(files) > max_attachments:
            raise serializers.ValidationError(f"حداکثر {max_attachments} فایل در هر ثبت مجاز است")
        max_size = max(getattr(settings, "ENTRY_MAX_FILE_SIZE_MB", 25), 1) * 1024 * 1024
        allowed_prefixes = tuple(getattr(settings, "ENTRY_ALLOWED_MIME_PREFIXES", []))
        for f in files:
            content_type = getattr(f, "content_type", "") or ""
            size = getattr(f, "size", 0) or 0
            if size > max_size:
                raise serializers.ValidationError("حجم فایل بیش از حد مجاز است")
            if allowed_prefixes and not any(content_type.startswith(prefix) for prefix in allowed_prefixes):
                raise serializers.ValidationError("نوع فایل مجاز نیست")
        attrs["text"] = text
        return attrs
