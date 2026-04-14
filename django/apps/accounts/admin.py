from django.contrib import admin

from .models import User, UserClientLogin


@admin.register(User)
class UserAdmin(admin.ModelAdmin):
    list_display = ("phone", "username", "display_name", "is_active", "is_staff", "created_at")
    search_fields = ("phone", "username", "display_name")


@admin.register(UserClientLogin)
class UserClientLoginAdmin(admin.ModelAdmin):
    list_display = ("user", "platform", "store", "app_version_name", "created_at")
    search_fields = ("user__phone", "device_model")
