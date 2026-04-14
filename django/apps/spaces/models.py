import uuid

from django.conf import settings
from django.db import models


class Space(models.Model):
    KIND_PERSONAL = "personal"
    KIND_SPACE = "space"
    KIND_CHOICES = [
        (KIND_PERSONAL, "Personal"),
        (KIND_SPACE, "Space"),
    ]

    id = models.UUIDField(primary_key=True, default=uuid.uuid4, editable=False)
    title = models.CharField(max_length=120)
    description = models.TextField(blank=True)
    kind = models.CharField(max_length=16, choices=KIND_CHOICES, default=KIND_SPACE)
    owner = models.ForeignKey(settings.AUTH_USER_MODEL, on_delete=models.CASCADE, related_name="owned_spaces")
    created_at = models.DateTimeField(auto_now_add=True)
    updated_at = models.DateTimeField(auto_now=True)

    class Meta:
        # Keep historical migration state under the accounts app.
        app_label = "accounts"
        db_table = "spaces"
        ordering = ["-updated_at"]

    def __str__(self):
        return self.title


class SpaceMembership(models.Model):
    ROLE_OWNER = "owner"
    ROLE_ADMIN = "admin"
    ROLE_VIEW_ALL = "view_all"
    ROLE_MEMBER = "member"
    ROLE_CHOICES = [
        (ROLE_OWNER, "Owner"),
        (ROLE_ADMIN, "Admin"),
        (ROLE_VIEW_ALL, "View All"),
        (ROLE_MEMBER, "Member"),
    ]

    id = models.UUIDField(primary_key=True, default=uuid.uuid4, editable=False)
    space = models.ForeignKey(Space, on_delete=models.CASCADE, related_name="memberships")
    user = models.ForeignKey(settings.AUTH_USER_MODEL, on_delete=models.CASCADE, related_name="space_memberships")
    role = models.CharField(max_length=20, choices=ROLE_CHOICES, default=ROLE_MEMBER)
    is_active = models.BooleanField(default=True)
    can_read_history = models.BooleanField(default=True)
    is_pinned = models.BooleanField(default=False)
    is_hidden = models.BooleanField(default=False)
    created_at = models.DateTimeField(auto_now_add=True)
    removed_at = models.DateTimeField(null=True, blank=True)

    class Meta:
        app_label = "accounts"
        db_table = "space_memberships"
        constraints = [
            models.UniqueConstraint(fields=["space", "user"], name="uniq_space_membership"),
        ]

    def __str__(self):
        return f"{self.space.title} - {self.user} ({self.role})"
