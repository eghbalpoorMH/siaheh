from django.conf import settings
from django.core.cache import cache
from rest_framework.permissions import AllowAny
from rest_framework.response import Response
from rest_framework.views import APIView

from config.celery import app as celery_app


class HealthCheckView(APIView):
    permission_classes = [AllowAny]

    def get(self, request):
        redis_status = "ok"
        celery_status = "ok"
        try:
            cache.set("healthcheck", "ok", timeout=5)
            cache.get("healthcheck")
        except Exception:
            redis_status = "error"
        try:
            if not celery_app.control.ping(timeout=0.5):
                celery_status = "error"
        except Exception:
            celery_status = "error"
        return Response(
            {
                "status": "ok" if redis_status == "ok" and celery_status == "ok" else "degraded",
                "services": {
                    "redis": redis_status,
                    "celery": celery_status,
                },
            }
        )


class OtpChannelsView(APIView):
    permission_classes = [AllowAny]

    def get(self, request):
        channels = [{"type": "sms", "name": "پیامک", "url": ""}]
        return Response({"channels": channels})


class AppVersionView(APIView):
    permission_classes = [AllowAny]

    def get(self, request):
        return Response(
            {
                "latest_version": settings.APP_VERSION_LATEST,
                "min_version": settings.APP_VERSION_MIN,
                "update_url": settings.APP_UPDATE_URL,
            }
        )

