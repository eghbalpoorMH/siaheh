import logging

from celery import shared_task

logger = logging.getLogger(__name__)


@shared_task(bind=True, max_retries=3, default_retry_delay=5)
def send_otp_task(self, phone: str, code: str):
    from apps.common.sms import send_otp

    try:
        success = send_otp(phone, code)
        if not success:
            raise RuntimeError(f"Kavenegar returned failure for {phone}")
        logger.info("OTP sent successfully to %s", phone)
    except Exception as exc:
        logger.error("OTP send failed for %s: %s", phone, exc)
        raise self.retry(exc=exc)

