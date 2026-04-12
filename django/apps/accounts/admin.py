from django.contrib import admin

from .models import Group, GroupMembership, Message, User, UserClientLogin


@admin.register(User)
class UserAdmin(admin.ModelAdmin):
    list_display = ("phone", "is_active", "is_staff", "created_at")
    search_fields = ("phone",)


@admin.register(UserClientLogin)
class UserClientLoginAdmin(admin.ModelAdmin):
    list_display = ("user", "platform", "store", "app_version_name", "created_at")
    search_fields = ("user__phone", "device_model")


@admin.register(Group)
class GroupAdmin(admin.ModelAdmin):
    list_display = ("title", "owner", "created_at")
    search_fields = ("title", "owner__phone")


@admin.register(GroupMembership)
class GroupMembershipAdmin(admin.ModelAdmin):
    list_display = ("group", "user", "role", "created_at")
    search_fields = ("group__title", "user__phone")


@admin.register(Message)
class MessageAdmin(admin.ModelAdmin):
    list_display = ("group", "sender", "short_text", "created_at")
    search_fields = ("group__title", "sender__phone", "text")

    def short_text(self, obj):
        return obj.text[:50]
