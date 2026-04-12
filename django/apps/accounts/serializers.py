from rest_framework import serializers

from .models import Group, GroupMembership, Message, User


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


class UserSerializer(serializers.ModelSerializer):
    class Meta:
        model = User
        fields = [
            "id",
            "phone",
            "created_at",
        ]


class GroupMembershipSerializer(serializers.ModelSerializer):
    user_id = serializers.UUIDField(source="user.id", read_only=True)
    phone = serializers.CharField(source="user.phone", read_only=True)

    class Meta:
        model = GroupMembership
        fields = [
            "id",
            "user_id",
            "phone",
            "role",
            "is_active",
            "can_read_history",
            "is_pinned",
            "is_hidden",
            "created_at",
            "removed_at",
        ]


class GroupSerializer(serializers.ModelSerializer):
    members_count = serializers.SerializerMethodField()
    role = serializers.SerializerMethodField()
    is_pinned = serializers.SerializerMethodField()
    is_hidden = serializers.SerializerMethodField()
    members = GroupMembershipSerializer(source="memberships", many=True, read_only=True)

    class Meta:
        model = Group
        fields = [
            "id",
            "title",
            "description",
            "members_count",
            "role",
            "is_pinned",
            "is_hidden",
            "members",
            "created_at",
            "updated_at",
        ]
        read_only_fields = ["id", "members_count", "role", "is_pinned", "is_hidden", "members"]

    def get_members_count(self, obj):
        return obj.memberships.count()

    def get_role(self, obj):
        request = self.context.get("request")
        user = getattr(request, "user", None)
        if not user or not user.is_authenticated:
            return None
        membership = obj.memberships.filter(user=user).first()
        return membership.role if membership else None

    def get_is_pinned(self, obj):
        request = self.context.get("request")
        user = getattr(request, "user", None)
        if not user or not user.is_authenticated:
            return False
        membership = obj.memberships.filter(user=user).first()
        return bool(membership and membership.is_pinned)

    def get_is_hidden(self, obj):
        request = self.context.get("request")
        user = getattr(request, "user", None)
        if not user or not user.is_authenticated:
            return False
        membership = obj.memberships.filter(user=user).first()
        return bool(membership and membership.is_hidden)


class GroupCreateSerializer(serializers.ModelSerializer):
    member_ids = serializers.ListField(
        child=serializers.UUIDField(),
        required=False,
        allow_empty=True,
        write_only=True,
    )

    class Meta:
        model = Group
        fields = ["id", "title", "description", "member_ids", "created_at", "updated_at"]
        read_only_fields = ["id", "created_at", "updated_at"]


class GroupMemberAddSerializer(serializers.Serializer):
    user_id = serializers.UUIDField()
    role = serializers.ChoiceField(choices=GroupMembership.ROLE_CHOICES, default=GroupMembership.ROLE_MEMBER)


class GroupMemberUpdateSerializer(serializers.Serializer):
    role = serializers.ChoiceField(choices=GroupMembership.ROLE_CHOICES, required=False)
    can_read_history = serializers.BooleanField(required=False)
    is_active = serializers.BooleanField(required=False)


class MessageSerializer(serializers.ModelSerializer):
    sender_id = serializers.UUIDField(source="sender.id", read_only=True)
    sender_phone = serializers.CharField(source="sender.phone", read_only=True)
    image_url = serializers.SerializerMethodField()

    class Meta:
        model = Message
        fields = [
            "id",
            "group_id",
            "sender_id",
            "sender_phone",
            "text",
            "image",
            "image_url",
            "created_at",
            "updated_at",
        ]
        read_only_fields = ["id", "sender_id", "sender_phone", "image_url"]

    def get_image_url(self, obj):
        if not obj.image:
            return None
        request = self.context.get("request")
        if request:
            return request.build_absolute_uri(obj.image.url)
        return obj.image.url

class MessageCreateSerializer(serializers.ModelSerializer):
    class Meta:
        model = Message
        fields = ["id", "text", "image", "created_at", "updated_at"]
        read_only_fields = ["id", "created_at", "updated_at"]

    def validate(self, attrs):
        if not attrs.get("text") and not attrs.get("image"):
            raise serializers.ValidationError("پیام باید متن یا عکس داشته باشد")
        return attrs
