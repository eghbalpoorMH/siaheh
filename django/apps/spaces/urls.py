from django.urls import path

from .views import (
    SpaceConvertView,
    SpaceDetailView,
    SpaceListCreateView,
    SpaceMemberAddView,
    SpaceMemberListView,
    SpaceMemberRemoveView,
    SpaceMemberUpdateView,
    SpacePreferenceView,
)

urlpatterns = [
    path("", SpaceListCreateView.as_view(), name="space-list-create"),
    path("<uuid:space_id>/", SpaceDetailView.as_view(), name="space-detail"),
    path("<uuid:space_id>/convert/", SpaceConvertView.as_view(), name="space-convert"),
    path("<uuid:space_id>/members/", SpaceMemberListView.as_view(), name="space-member-list"),
    path("<uuid:space_id>/members/add/", SpaceMemberAddView.as_view(), name="space-member-add"),
    path("<uuid:space_id>/members/<uuid:user_id>/", SpaceMemberUpdateView.as_view(), name="space-member-update"),
    path("<uuid:space_id>/members/<uuid:user_id>/remove/", SpaceMemberRemoveView.as_view(), name="space-member-remove"),
    path("<uuid:space_id>/preferences/", SpacePreferenceView.as_view(), name="space-preferences"),
]
