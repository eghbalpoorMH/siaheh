from django.contrib import admin

from apps.spaces.models import Space, SpaceMembership


@admin.register(Space)
class SpaceAdmin(admin.ModelAdmin):
    list_display = ("title", "kind", "owner", "created_at")
    search_fields = ("title", "owner__phone", "owner__username")


@admin.register(SpaceMembership)
class SpaceMembershipAdmin(admin.ModelAdmin):
    list_display = ("space", "user", "role", "created_at")
    search_fields = ("space__title", "user__phone", "user__username")
