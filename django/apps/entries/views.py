import mimetypes
from uuid import UUID

from django.db import transaction
from django.utils import timezone
from django.utils.dateparse import parse_date, parse_datetime
from rest_framework import status
from rest_framework.pagination import PageNumberPagination
from rest_framework.parsers import FormParser, JSONParser, MultiPartParser
from rest_framework.permissions import IsAuthenticated
from rest_framework.response import Response
from rest_framework.views import APIView

from apps.entries.models import Message, MessageAttachment
from apps.entries.serializers import MessageCreateSerializer, MessageSerializer
from apps.spaces.models import Space
from apps.spaces.policies import get_membership, get_space_for_user_or_404, visible_messages_queryset


class EntryPagination(PageNumberPagination):
    page_size = 40
    page_size_query_param = "page_size"
    max_page_size = 100


def detect_attachment_kind(content_type: str | None, file_name: str, declared_kind: str | None = None) -> str:
    if declared_kind in {
        MessageAttachment.KIND_IMAGE,
        MessageAttachment.KIND_VIDEO,
        MessageAttachment.KIND_MUSIC,
        MessageAttachment.KIND_VOICE,
        MessageAttachment.KIND_DOCUMENT,
        MessageAttachment.KIND_FILE,
    }:
        return declared_kind
    mime = content_type or mimetypes.guess_type(file_name)[0] or ""
    if mime.startswith("image/"):
        return MessageAttachment.KIND_IMAGE
    if mime.startswith("video/"):
        return MessageAttachment.KIND_VIDEO
    if mime.startswith("audio/"):
        return MessageAttachment.KIND_MUSIC
    if mime in {"application/pdf"} or mime.startswith("text/"):
        return MessageAttachment.KIND_DOCUMENT
    return MessageAttachment.KIND_FILE


class SpaceMessageListCreateView(APIView):
    permission_classes = [IsAuthenticated]
    parser_classes = [JSONParser, MultiPartParser, FormParser]

    def get(self, request, space_id):
        space = get_space_for_user_or_404(space_id, request.user)
        membership = get_membership(space, request.user)
        if not membership or not membership.can_read_history:
            return Response(
                {"error": {"code": "FORBIDDEN", "message": "دسترسی به تاریخچه این فضا ندارید"}},
                status=status.HTTP_403_FORBIDDEN,
            )
        queryset = visible_messages_queryset(space, request.user)

        search = request.query_params.get("search", "").strip()
        sender = request.query_params.get("sender")
        sender_username = request.query_params.get("sender_username")
        date_from = request.query_params.get("date_from")
        date_to = request.query_params.get("date_to")

        if search:
            queryset = queryset.filter(text__icontains=search)
        if sender:
            try:
                queryset = queryset.filter(sender_id=UUID(sender))
            except ValueError:
                return Response(
                    {"error": {"code": "INVALID_SENDER", "message": "شناسه فرستنده نامعتبر است"}},
                    status=status.HTTP_400_BAD_REQUEST,
                )
        if sender_username:
            queryset = queryset.filter(sender__username__iexact=sender_username.strip().lower())
        if date_from:
            dt = parse_datetime(date_from) or None
            if dt is None:
                d = parse_date(date_from)
                if d is None:
                    return Response(
                        {"error": {"code": "INVALID_DATE", "message": "فرمت تاریخ شروع نامعتبر است"}},
                        status=status.HTTP_400_BAD_REQUEST,
                    )
                dt = timezone.make_aware(timezone.datetime.combine(d, timezone.datetime.min.time()))
            queryset = queryset.filter(created_at__gte=dt)
        if date_to:
            dt = parse_datetime(date_to) or None
            if dt is None:
                d = parse_date(date_to)
                if d is None:
                    return Response(
                        {"error": {"code": "INVALID_DATE", "message": "فرمت تاریخ پایان نامعتبر است"}},
                        status=status.HTTP_400_BAD_REQUEST,
                    )
                dt = timezone.make_aware(timezone.datetime.combine(d, timezone.datetime.max.time()))
            queryset = queryset.filter(created_at__lte=dt)

        paginator = EntryPagination()
        page = paginator.paginate_queryset(queryset.distinct(), request)
        serializer = MessageSerializer(page, many=True, context={"request": request})
        return paginator.get_paginated_response({"entries": serializer.data})

    def post(self, request, space_id):
        space = get_space_for_user_or_404(space_id, request.user)
        serializer = MessageCreateSerializer(data=request.data)
        serializer.is_valid(raise_exception=True)
        membership = get_membership(space, request.user)
        if not membership or not membership.is_active:
            return Response(
                {"error": {"code": "FORBIDDEN", "message": "امکان ثبت رویداد در این فضا ندارید"}},
                status=status.HTTP_403_FORBIDDEN,
            )

        attachments = serializer.validated_data.pop("attachments", [])
        attachment_kinds = serializer.validated_data.pop("attachment_kinds", [])
        with transaction.atomic():
            message = Message.objects.create(space=space, sender=request.user, text=serializer.validated_data.get("text", ""))
            files = list(attachments)
            for index, f in enumerate(files):
                declared_kind = attachment_kinds[index] if index < len(attachment_kinds) else None
                MessageAttachment.objects.create(
                    message=message,
                    file=f,
                    kind=detect_attachment_kind(
                        getattr(f, "content_type", None),
                        getattr(f, "name", ""),
                        declared_kind=declared_kind,
                    ),
                    original_name=getattr(f, "name", "") or "",
                    sort_order=index,
                )
            Space.objects.filter(id=space.id).update(updated_at=timezone.now())

        message = Message.objects.select_related("sender").prefetch_related("attachments").get(id=message.id)
        return Response(
            MessageSerializer(message, context={"request": request}).data,
            status=status.HTTP_201_CREATED,
        )
