from django.db.models import Q
from django.shortcuts import get_object_or_404

from .models import Space, SpaceMembership, Message, User


def get_space_for_user_or_404(space_id, user: User) -> Space:
    return get_object_or_404(
        Space.objects.prefetch_related("memberships__user"),
        id=space_id,
        memberships__user=user,
    )


def get_membership(space: Space, user: User) -> SpaceMembership | None:
    return space.memberships.filter(user=user).first()


def is_space_admin(space: Space, user: User) -> bool:
    return space.memberships.filter(
        user=user,
        role__in=[SpaceMembership.ROLE_OWNER, SpaceMembership.ROLE_ADMIN],
        is_active=True,
    ).exists()


def can_manage_members(space: Space, user: User) -> bool:
    return is_space_admin(space, user)


def can_view_all(space: Space, user: User) -> bool:
    return space.memberships.filter(
        user=user,
        role__in=[SpaceMembership.ROLE_OWNER, SpaceMembership.ROLE_ADMIN, SpaceMembership.ROLE_VIEW_ALL],
        is_active=True,
    ).exists()


def visible_messages_queryset(space: Space, user: User):
    queryset = (
        Message.objects.filter(space=space)
        .select_related("sender")
        .prefetch_related("attachments")
        .order_by("-created_at")
    )
    if can_view_all(space, user):
        return queryset
    return queryset.filter(Q(sender=user))
