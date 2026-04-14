from django.conf import settings
from rest_framework import serializers

from apps.entries.models import Message, MessageAttachment
from apps.identity.serializers import PublicUserSerializer


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
