from django.conf import settings
from django.conf.urls.static import static
from django.contrib import admin
from django.urls import include, path
from drf_spectacular.views import (
    SpectacularAPIView,
    SpectacularRedocView,
    SpectacularSwaggerView,
)

from apps.common.views import AppVersionView, HealthCheckView, OtpChannelsView

urlpatterns = [
    path("admin/", admin.site.urls),
    path("health/", HealthCheckView.as_view()),
    path("api/v1/app-version/", AppVersionView.as_view()),
    path("api/v1/otp-channels/", OtpChannelsView.as_view()),
    path("api/schema/", SpectacularAPIView.as_view(), name="schema"),
    path("api/docs/", SpectacularSwaggerView.as_view(url_name="schema"), name="swagger-ui"),
    path("api/redoc/", SpectacularRedocView.as_view(url_name="schema"), name="redoc"),
    path("api/v1/auth/", include("apps.identity.urls")),
    path("api/v1/users/", include("apps.identity.user_urls")),
    path("api/v1/spaces/", include("apps.spaces.urls")),
    path("api/v1/spaces/", include("apps.entries.urls")),
]

if not settings.USE_S3:
    urlpatterns += static(settings.MEDIA_URL, document_root=settings.MEDIA_ROOT)
