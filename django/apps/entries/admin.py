from django.contrib import admin

from apps.entries.models import Message, MessageAttachment


@admin.register(Message)
class MessageAdmin(admin.ModelAdmin):
    list_display = ("space", "sender", "short_text", "created_at")
    search_fields = ("space__title", "sender__phone", "sender__username", "text")

    def short_text(self, obj):
        return obj.text[:50]


@admin.register(MessageAttachment)
class MessageAttachmentAdmin(admin.ModelAdmin):
    list_display = ("message", "kind", "original_name", "sort_order", "created_at")
    search_fields = ("message__space__title", "message__sender__username", "original_name")
