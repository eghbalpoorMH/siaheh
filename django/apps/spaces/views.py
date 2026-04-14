from django.db import transaction
from django.db.models import Prefetch
from django.shortcuts import get_object_or_404
from django.utils import timezone
from rest_framework import status
from rest_framework.permissions import IsAuthenticated
from rest_framework.response import Response
from rest_framework.views import APIView

from apps.accounts.models import User
from apps.spaces.models import Space, SpaceMembership
from apps.spaces.policies import (
    can_manage_members,
    get_membership,
    get_space_for_user_or_404,
    is_space_admin,
)
from apps.spaces.serializers import (
    SpaceCreateSerializer,
    SpaceMemberAddSerializer,
    SpaceMemberUpdateSerializer,
    SpaceMembershipSerializer,
    SpaceSerializer,
)


class SpaceListCreateView(APIView):
    permission_classes = [IsAuthenticated]

    def get(self, request):
        spaces = (
            Space.objects.filter(
                memberships__user=request.user,
                memberships__can_read_history=True,
                memberships__is_hidden=False,
            )
            .prefetch_related(
                Prefetch("memberships", queryset=SpaceMembership.objects.select_related("user").order_by("created_at"))
            )
            .distinct()
            .order_by("-memberships__is_pinned", "-updated_at")
        )
        serializer = SpaceSerializer(spaces, many=True, context={"request": request})
        return Response({"spaces": serializer.data})

    def post(self, request):
        serializer = SpaceCreateSerializer(data=request.data)
        serializer.is_valid(raise_exception=True)
        member_ids = serializer.validated_data.pop("member_ids", [])

        with transaction.atomic():
            space = serializer.save(owner=request.user, kind=Space.KIND_SPACE)
            SpaceMembership.objects.create(
                space=space,
                user=request.user,
                role=SpaceMembership.ROLE_OWNER,
                is_pinned=True,
            )
            if member_ids:
                members = User.objects.filter(id__in=member_ids).exclude(id=request.user.id)
                SpaceMembership.objects.bulk_create(
                    [
                        SpaceMembership(space=space, user=member, role=SpaceMembership.ROLE_MEMBER)
                        for member in members
                    ],
                    ignore_conflicts=True,
                )

        space = Space.objects.prefetch_related("memberships__user").get(id=space.id)
        return Response(
            SpaceSerializer(space, context={"request": request}).data,
            status=status.HTTP_201_CREATED,
        )


class SpaceDetailView(APIView):
    permission_classes = [IsAuthenticated]

    def get(self, request, space_id):
        space = get_space_for_user_or_404(space_id, request.user)
        serializer = SpaceSerializer(space, context={"request": request})
        return Response(serializer.data)

    def patch(self, request, space_id):
        space = get_space_for_user_or_404(space_id, request.user)
        if not is_space_admin(space, request.user):
            return Response(
                {"error": {"code": "FORBIDDEN", "message": "فقط مدیر فضا می‌تواند فضا را ویرایش کند"}},
                status=status.HTTP_403_FORBIDDEN,
            )
        serializer = SpaceCreateSerializer(space, data=request.data, partial=True)
        serializer.is_valid(raise_exception=True)
        serializer.save()
        space.refresh_from_db()
        space = Space.objects.prefetch_related("memberships__user").get(id=space.id)
        return Response(SpaceSerializer(space, context={"request": request}).data)


class SpaceConvertView(APIView):
    permission_classes = [IsAuthenticated]

    def post(self, request, space_id):
        space = get_space_for_user_or_404(space_id, request.user)
        if space.owner_id != request.user.id:
            return Response(
                {"error": {"code": "FORBIDDEN", "message": "فقط مالک می‌تواند فضا شخصی را تبدیل کند"}},
                status=status.HTTP_403_FORBIDDEN,
            )
        if space.kind != Space.KIND_PERSONAL:
            return Response({"error": {"code": "INVALID_STATE", "message": "این فضا شخصی نیست"}}, status=400)
        if request.data.get("confirm") is not True:
            return Response(
                {"error": {"code": "CONFIRM_REQUIRED", "message": "برای تبدیل باید confirm=true ارسال شود"}},
                status=400,
            )
        space.kind = Space.KIND_SPACE
        space.save(update_fields=["kind"])
        return Response(SpaceSerializer(space, context={"request": request}).data)


class SpaceMemberAddView(APIView):
    permission_classes = [IsAuthenticated]

    def post(self, request, space_id):
        space = get_space_for_user_or_404(space_id, request.user)
        if not can_manage_members(space, request.user):
            return Response(
                {"error": {"code": "FORBIDDEN", "message": "فقط مدیر فضا می‌تواند عضو اضافه کند"}},
                status=status.HTTP_403_FORBIDDEN,
            )
        serializer = SpaceMemberAddSerializer(data=request.data)
        serializer.is_valid(raise_exception=True)
        user_id = serializer.validated_data.get("user_id")
        username = serializer.validated_data.get("username")
        if user_id:
            member = get_object_or_404(User, id=user_id)
        else:
            member = get_object_or_404(User, username=username.lower())
        membership, created = SpaceMembership.objects.update_or_create(
            space=space,
            user=member,
            defaults={
                "role": serializer.validated_data["role"],
                "is_active": True,
                "can_read_history": True,
                "removed_at": None,
            },
        )
        status_code = status.HTTP_201_CREATED if created else status.HTTP_200_OK
        return Response(SpaceMembershipSerializer(membership, context={"request": request}).data, status=status_code)


class SpaceMemberListView(APIView):
    permission_classes = [IsAuthenticated]

    def get(self, request, space_id):
        space = get_space_for_user_or_404(space_id, request.user)
        if not can_manage_members(space, request.user):
            return Response(
                {"error": {"code": "FORBIDDEN", "message": "فقط مدیر فضا می‌تواند لیست اعضا را ببیند"}},
                status=status.HTTP_403_FORBIDDEN,
            )
        memberships = space.memberships.select_related("user").order_by("created_at")
        return Response({"members": SpaceMembershipSerializer(memberships, many=True, context={"request": request}).data})


class SpaceMemberUpdateView(APIView):
    permission_classes = [IsAuthenticated]

    def patch(self, request, space_id, user_id):
        space = get_space_for_user_or_404(space_id, request.user)
        if not can_manage_members(space, request.user):
            return Response(
                {"error": {"code": "FORBIDDEN", "message": "فقط مدیر فضا می‌تواند نقش اعضا را تغییر دهد"}},
                status=status.HTTP_403_FORBIDDEN,
            )
        membership = get_object_or_404(SpaceMembership, space=space, user_id=user_id)
        if membership.role == SpaceMembership.ROLE_OWNER:
            return Response(
                {"error": {"code": "FORBIDDEN", "message": "نقش مالک قابل تغییر نیست"}},
                status=status.HTTP_403_FORBIDDEN,
            )
        serializer = SpaceMemberUpdateSerializer(data=request.data)
        serializer.is_valid(raise_exception=True)
        updates = []
        if "role" in serializer.validated_data:
            membership.role = serializer.validated_data["role"]
            updates.append("role")
        if "can_read_history" in serializer.validated_data:
            membership.can_read_history = serializer.validated_data["can_read_history"]
            updates.append("can_read_history")
        if "is_active" in serializer.validated_data:
            membership.is_active = serializer.validated_data["is_active"]
            updates.append("is_active")
            membership.removed_at = timezone.now() if not membership.is_active else None
            updates.append("removed_at")
        if updates:
            membership.save(update_fields=updates)
        return Response(SpaceMembershipSerializer(membership, context={"request": request}).data)


class SpaceMemberRemoveView(APIView):
    permission_classes = [IsAuthenticated]

    def delete(self, request, space_id, user_id):
        space = get_space_for_user_or_404(space_id, request.user)
        if not can_manage_members(space, request.user):
            return Response(
                {"error": {"code": "FORBIDDEN", "message": "فقط مدیر فضا می‌تواند عضو را حذف کند"}},
                status=status.HTTP_403_FORBIDDEN,
            )
        membership = get_object_or_404(SpaceMembership, space=space, user_id=user_id)
        if membership.role == SpaceMembership.ROLE_OWNER:
            return Response(
                {"error": {"code": "FORBIDDEN", "message": "نقش مالک قابل حذف نیست"}},
                status=status.HTTP_403_FORBIDDEN,
            )
        membership.is_active = False
        membership.removed_at = timezone.now()
        membership.save(update_fields=["is_active", "removed_at"])
        return Response(SpaceMembershipSerializer(membership, context={"request": request}).data)


class SpacePreferenceView(APIView):
    permission_classes = [IsAuthenticated]

    def patch(self, request, space_id):
        space = get_space_for_user_or_404(space_id, request.user)
        membership = get_membership(space, request.user)
        if not membership:
            return Response(
                {"error": {"code": "NOT_FOUND", "message": "عضویت یافت نشد"}},
                status=status.HTTP_404_NOT_FOUND,
            )
        is_pinned = request.data.get("is_pinned")
        is_hidden = request.data.get("is_hidden")
        updates = []
        if isinstance(is_pinned, bool):
            membership.is_pinned = is_pinned
            updates.append("is_pinned")
        if isinstance(is_hidden, bool):
            membership.is_hidden = is_hidden
            updates.append("is_hidden")
        if updates:
            membership.save(update_fields=updates)
        return Response(SpaceMembershipSerializer(membership, context={"request": request}).data)
