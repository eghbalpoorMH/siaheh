from django.urls import path

from .views import OTPRequestView, TokenView

urlpatterns = [
    path("otp/", OTPRequestView.as_view(), name="otp-request"),
    path("tokens/", TokenView.as_view(), name="token"),
]
