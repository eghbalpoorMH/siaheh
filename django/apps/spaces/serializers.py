from rest_framework import serializers

from apps.identity.serializers import PublicUserSerializer
from apps.spaces.models import Space, SpaceMembership


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
        read_only_fields = [
            "id",
            "members_count",
            "role",
            "is_pinned",
            "is_hidden",
            "latest_entry_preview",
            "members",
            "kind",
        ]

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
