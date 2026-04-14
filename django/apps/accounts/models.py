import uuid

from django.contrib.auth.models import AbstractBaseUser, BaseUserManager, PermissionsMixin
from django.db import models


class UserManager(BaseUserManager):
    def create_user(self, phone, **extra_fields):
        if not phone:
            raise ValueError("Phone number is required")
        if not extra_fields.get("username"):
            extra_fields["username"] = f"user{phone[-6:]}"
        user = self.model(phone=phone, **extra_fields)
        user.set_unusable_password()
        user.save(using=self._db)
        return user

    def create_superuser(self, phone, password=None, **extra_fields):
        extra_fields.setdefault("is_staff", True)
        extra_fields.setdefault("is_superuser", True)
        extra_fields.setdefault("username", f"admin{phone[-6:]}")
        user = self.model(phone=phone, **extra_fields)
        if password:
            user.set_password(password)
        user.save(using=self._db)
        return user


class User(AbstractBaseUser, PermissionsMixin):
    id = models.UUIDField(primary_key=True, default=uuid.uuid4, editable=False)
    phone = models.CharField(max_length=11, unique=True)
    username = models.CharField(max_length=32, unique=True, db_index=True)
    display_name = models.CharField(max_length=80, blank=True, default="")
    avatar = models.ImageField(upload_to="avatars/%Y/%m/%d/", null=True, blank=True)
    about = models.CharField(max_length=280, blank=True, default="")
    is_active = models.BooleanField(default=True)
    is_staff = models.BooleanField(default=False)
    created_at = models.DateTimeField(auto_now_add=True)
    updated_at = models.DateTimeField(auto_now=True)

    objects = UserManager()

    USERNAME_FIELD = "phone"
    REQUIRED_FIELDS = []

    class Meta:
        db_table = "users"

    def __str__(self):
        return self.phone


class UserClientLogin(models.Model):
    PLATFORM_ANDROID = "android"
    PLATFORM_IOS = "ios"
    PLATFORM_WEB = "web"
    PLATFORM_UNKNOWN = "unknown"
    PLATFORM_CHOICES = [
        (PLATFORM_ANDROID, "Android"),
        (PLATFORM_IOS, "iOS"),
        (PLATFORM_WEB, "Web"),
        (PLATFORM_UNKNOWN, "Unknown"),
    ]

    id = models.UUIDField(primary_key=True, default=uuid.uuid4, editable=False)
    user = models.ForeignKey(User, on_delete=models.CASCADE, related_name="client_logins")
    platform = models.CharField(max_length=20, choices=PLATFORM_CHOICES, default=PLATFORM_ANDROID)
    store = models.CharField(max_length=30, default="unknown")
    app_version_name = models.CharField(max_length=32, null=True, blank=True)
    app_version_code = models.IntegerField(null=True, blank=True)
    device_model = models.CharField(max_length=100, null=True, blank=True)
    os_version = models.CharField(max_length=50, null=True, blank=True)
    ip_address = models.GenericIPAddressField(null=True, blank=True)
    user_agent = models.CharField(max_length=500, null=True, blank=True)
    created_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        db_table = "user_client_logins"


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
    owner = models.ForeignKey(User, on_delete=models.CASCADE, related_name="owned_spaces")
    created_at = models.DateTimeField(auto_now_add=True)
    updated_at = models.DateTimeField(auto_now=True)

    class Meta:
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
    user = models.ForeignKey(User, on_delete=models.CASCADE, related_name="space_memberships")
    role = models.CharField(max_length=20, choices=ROLE_CHOICES, default=ROLE_MEMBER)
    is_active = models.BooleanField(default=True)
    can_read_history = models.BooleanField(default=True)
    is_pinned = models.BooleanField(default=False)
    is_hidden = models.BooleanField(default=False)
    created_at = models.DateTimeField(auto_now_add=True)
    removed_at = models.DateTimeField(null=True, blank=True)

    class Meta:
        db_table = "space_memberships"
        constraints = [
            models.UniqueConstraint(fields=["space", "user"], name="uniq_space_membership"),
        ]

    def __str__(self):
        return f"{self.space.title} - {self.user.phone} ({self.role})"


class Message(models.Model):
    id = models.UUIDField(primary_key=True, default=uuid.uuid4, editable=False)
    space = models.ForeignKey(Space, on_delete=models.CASCADE, related_name="messages")
    sender = models.ForeignKey(User, on_delete=models.CASCADE, related_name="messages")
    text = models.TextField(blank=True, default="")
    created_at = models.DateTimeField(auto_now_add=True)
    updated_at = models.DateTimeField(auto_now=True)

    class Meta:
        db_table = "messages"
        ordering = ["created_at"]

    def __str__(self):
        return f"{self.sender.phone}: {self.text[:40]}"


class MessageAttachment(models.Model):
    KIND_IMAGE = "image"
    KIND_VIDEO = "video"
    KIND_MUSIC = "music"
    KIND_VOICE = "voice"
    KIND_DOCUMENT = "document"
    KIND_FILE = "file"
    KIND_CHOICES = [
        (KIND_IMAGE, "Image"),
        (KIND_VIDEO, "Video"),
        (KIND_MUSIC, "Music"),
        (KIND_VOICE, "Voice"),
        (KIND_DOCUMENT, "Document"),
        (KIND_FILE, "File"),
    ]

    id = models.UUIDField(primary_key=True, default=uuid.uuid4, editable=False)
    message = models.ForeignKey(Message, on_delete=models.CASCADE, related_name="attachments")
    file = models.FileField(upload_to="entries/%Y/%m/%d/")
    kind = models.CharField(max_length=16, choices=KIND_CHOICES, default=KIND_FILE)
    original_name = models.CharField(max_length=255, blank=True, default="")
    sort_order = models.PositiveIntegerField(default=0)
    created_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        db_table = "message_attachments"
        ordering = ["sort_order", "created_at"]
