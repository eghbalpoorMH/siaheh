from __future__ import annotations

import logging

from django.conf import settings
from kavenegar import APIException, HTTPException, KavenegarAPI

logger = logging.getLogger(__name__)


class SmsService:
    def __init__(self):
        self.api_key = getattr(settings, "KAVENEGAR_API_KEY", "")
        self.template = getattr(settings, "KAVENEGAR_TEMPLATE", "")

    def send_otp(self, receptor: str, code: str) -> bool:
        if settings.DEBUG:
            logger.info("[DEBUG-SMS] OTP for %s: %s", receptor, code)
            if not self.api_key:
                return True

        if not self.api_key:
            logger.warning("KAVENEGAR_API_KEY is not configured; SMS not sent to %s", receptor)
            return False

        try:
            api = KavenegarAPI(self.api_key)
            api.verify_lookup(
                {
                    "receptor": receptor,
                    "token": code,
                    "template": self.template,
                }
            )
            logger.info("OTP sent via Kavenegar to %s", receptor)
            return True
        except (APIException, HTTPException) as exc:
            logger.exception("Kavenegar SMS failed for %s: %s", receptor, exc)
            return False


def send_otp(receptor: str, code: str) -> bool:
    return SmsService().send_otp(receptor, code)

