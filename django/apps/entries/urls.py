from django.urls import path

from .views import SpaceMessageListCreateView

urlpatterns = [
    path("<uuid:space_id>/messages/", SpaceMessageListCreateView.as_view(), name="space-messages"),
    path("<uuid:space_id>/entries/", SpaceMessageListCreateView.as_view(), name="space-entries"),
]
