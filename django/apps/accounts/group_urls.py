from django.urls import path

from .views import (
    GroupDetailView,
    GroupListCreateView,
    GroupMemberAddView,
    GroupMemberListView,
    GroupMemberRemoveView,
    GroupMemberUpdateView,
    GroupMessageListCreateView,
    GroupPreferenceView,
)

urlpatterns = [
    path("", GroupListCreateView.as_view(), name="group-list-create"),
    path("<uuid:group_id>/", GroupDetailView.as_view(), name="group-detail"),
    path("<uuid:group_id>/members/", GroupMemberListView.as_view(), name="group-member-list"),
    path("<uuid:group_id>/members/add/", GroupMemberAddView.as_view(), name="group-member-add"),
    path("<uuid:group_id>/members/<uuid:user_id>/", GroupMemberUpdateView.as_view(), name="group-member-update"),
    path("<uuid:group_id>/members/<uuid:user_id>/remove/", GroupMemberRemoveView.as_view(), name="group-member-remove"),
    path("<uuid:group_id>/messages/", GroupMessageListCreateView.as_view(), name="group-messages"),
    path("<uuid:group_id>/preferences/", GroupPreferenceView.as_view(), name="group-preferences"),
]
