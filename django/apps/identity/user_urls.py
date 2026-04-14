from django.urls import path

from .views import ContactDiscoveryView, MeView, ProfileView, UserSearchView

urlpatterns = [
    path("me/", MeView.as_view(), name="me"),
    path("profile/", ProfileView.as_view(), name="profile"),
    path("search/", UserSearchView.as_view(), name="user-search"),
    path("discover-contacts/", ContactDiscoveryView.as_view(), name="discover-contacts"),
]
