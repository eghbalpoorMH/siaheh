from django.contrib import admin

from .models import Space, SpaceMembership, Message, MessageAttachment, User, UserClientLogin


@admin.register(User)
class UserAdmin(admin.ModelAdmin):
    list_display = ("phone", "username", "display_name", "is_active", "is_staff", "created_at")
    search_fields = ("phone", "username", "display_name")


@admin.register(UserClientLogin)
class UserClientLoginAdmin(admin.ModelAdmin):
    list_display = ("user", "platform", "store", "app_version_name", "created_at")
    search_fields = ("user__phone", "device_model")


@admin.register(Space)
class SpaceAdmin(admin.ModelAdmin):
    list_display = ("title", "kind", "owner", "created_at")
    search_fields = ("title", "owner__phone", "owner__username")


@admin.register(SpaceMembership)
class SpaceMembershipAdmin(admin.ModelAdmin):
    list_display = ("space", "user", "role", "created_at")
    search_fields = ("space__title", "user__phone", "user__username")


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
