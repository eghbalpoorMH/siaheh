import uuid

from django.conf import settings
from django.db import models

from apps.spaces.models import Space


class Message(models.Model):
    id = models.UUIDField(primary_key=True, default=uuid.uuid4, editable=False)
    space = models.ForeignKey(Space, on_delete=models.CASCADE, related_name="messages")
    sender = models.ForeignKey(settings.AUTH_USER_MODEL, on_delete=models.CASCADE, related_name="messages")
    text = models.TextField(blank=True, default="")
    created_at = models.DateTimeField(auto_now_add=True)
    updated_at = models.DateTimeField(auto_now=True)

    class Meta:
        app_label = "accounts"
        db_table = "messages"
        ordering = ["created_at"]

    def __str__(self):
        return f"{self.sender}: {self.text[:40]}"


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
        app_label = "accounts"
        db_table = "message_attachments"
        ordering = ["sort_order", "created_at"]
