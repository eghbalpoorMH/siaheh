from datetime import timedelta
from pathlib import Path

from decouple import Csv, config

BASE_DIR = Path(__file__).resolve().parent.parent

SECRET_KEY = config("DJANGO_SECRET_KEY", default="change-me")
DEBUG = config("DJANGO_DEBUG", default=False, cast=bool)
ALLOWED_HOSTS = config("DJANGO_ALLOWED_HOSTS", default="", cast=Csv())

ROOT_URLCONF = "config.urls"
WSGI_APPLICATION = "config.wsgi.application"
ASGI_APPLICATION = "config.asgi.application"
DEFAULT_AUTO_FIELD = "django.db.models.BigAutoField"
AUTH_USER_MODEL = "accounts.User"

INSTALLED_APPS = [
    "django.contrib.admin",
    "django.contrib.auth",
    "django.contrib.contenttypes",
    "django.contrib.sessions",
    "django.contrib.messages",
    "django.contrib.staticfiles",
    "storages",
    "rest_framework",
    "rest_framework_simplejwt.token_blacklist",
    "corsheaders",
    "drf_spectacular",
    "apps.accounts",
    "apps.common",
]

MIDDLEWARE = [
    "corsheaders.middleware.CorsMiddleware",
    "django.middleware.security.SecurityMiddleware",
    "django.contrib.sessions.middleware.SessionMiddleware",
    "django.middleware.common.CommonMiddleware",
    "django.middleware.csrf.CsrfViewMiddleware",
    "django.contrib.auth.middleware.AuthenticationMiddleware",
    "django.contrib.messages.middleware.MessageMiddleware",
    "django.middleware.clickjacking.XFrameOptionsMiddleware",
]

TEMPLATES = [
    {
        "BACKEND": "django.template.backends.django.DjangoTemplates",
        "DIRS": [],
        "APP_DIRS": True,
        "OPTIONS": {
            "context_processors": [
                "django.template.context_processors.debug",
                "django.template.context_processors.request",
                "django.contrib.auth.context_processors.auth",
                "django.contrib.messages.context_processors.messages",
            ],
        },
    },
]

DATABASES = {
    "default": {
        "ENGINE": "django.db.backends.postgresql",
        "NAME": config("POSTGRES_DB", default="siaheh"),
        "USER": config("POSTGRES_USER", default="siaheh"),
        "PASSWORD": config("POSTGRES_PASSWORD", default="siaheh"),
        "HOST": config("POSTGRES_HOST", default="localhost"),
        "PORT": config("POSTGRES_PORT", default="5432"),
    }
}

CACHES = {
    "default": {
        "BACKEND": "django.core.cache.backends.redis.RedisCache",
        "LOCATION": config("REDIS_URL", default="redis://localhost:6379/0"),
    }
}

AUTH_PASSWORD_VALIDATORS = [
    {"NAME": "django.contrib.auth.password_validation.UserAttributeSimilarityValidator"},
    {"NAME": "django.contrib.auth.password_validation.MinimumLengthValidator"},
    {"NAME": "django.contrib.auth.password_validation.CommonPasswordValidator"},
    {"NAME": "django.contrib.auth.password_validation.NumericPasswordValidator"},
]

LANGUAGE_CODE = "fa-ir"
TIME_ZONE = "Asia/Tehran"
USE_I18N = True
USE_TZ = True

MAX_UPLOAD_SIZE_MB = config("MAX_UPLOAD_SIZE_MB", default=10, cast=int)

ARVAN_ENDPOINT_URL = config(
    "ARVAN_ENDPOINT_URL",
    default="https://s3.ir-thr-at1.arvanstorage.ir",
)
ARVAN_ACCESS_KEY_ID = config("ARVAN_ACCESS_KEY_ID", default="")
ARVAN_SECRET_ACCESS_KEY = config("ARVAN_SECRET_ACCESS_KEY", default="")
ARVAN_STATIC_BUCKET = config("ARVAN_STATIC_BUCKET", default="siaheh-static")
ARVAN_MEDIA_BUCKET = config("ARVAN_MEDIA_BUCKET", default="siaheh-media")
ARVAN_STATIC_CUSTOM_DOMAIN = config("ARVAN_STATIC_CUSTOM_DOMAIN", default="")
ARVAN_MEDIA_CUSTOM_DOMAIN = config("ARVAN_MEDIA_CUSTOM_DOMAIN", default="")
ARVAN_STATIC_LOCATION = config("ARVAN_STATIC_LOCATION", default="static")
ARVAN_MEDIA_LOCATION = config("ARVAN_MEDIA_LOCATION", default="media")
USE_S3 = config("USE_S3", default=not DEBUG, cast=bool)

if USE_S3:
    STORAGES = {
        "default": {
            "BACKEND": "storages.backends.s3.S3Storage",
            "OPTIONS": {
                "access_key": ARVAN_ACCESS_KEY_ID,
                "secret_key": ARVAN_SECRET_ACCESS_KEY,
                "bucket_name": ARVAN_MEDIA_BUCKET,
                "endpoint_url": ARVAN_ENDPOINT_URL,
                "location": ARVAN_MEDIA_LOCATION,
                "file_overwrite": False,
                "querystring_auth": True,
                "querystring_expire": 3600,
            },
        },
        "staticfiles": {
            "BACKEND": "storages.backends.s3.S3Storage",
            "OPTIONS": {
                "access_key": ARVAN_ACCESS_KEY_ID,
                "secret_key": ARVAN_SECRET_ACCESS_KEY,
                "bucket_name": ARVAN_STATIC_BUCKET,
                "endpoint_url": ARVAN_ENDPOINT_URL,
                "location": ARVAN_STATIC_LOCATION,
                "custom_domain": ARVAN_STATIC_CUSTOM_DOMAIN or None,
                "querystring_auth": False,
                "default_acl": "public-read",
            },
        },
    }
    MEDIA_URL = (
        f"https://{ARVAN_MEDIA_CUSTOM_DOMAIN}/{ARVAN_MEDIA_LOCATION}/"
        if ARVAN_MEDIA_CUSTOM_DOMAIN
        else f"{ARVAN_ENDPOINT_URL}/{ARVAN_MEDIA_BUCKET}/{ARVAN_MEDIA_LOCATION}/"
    )
    STATIC_URL = (
        f"https://{ARVAN_STATIC_CUSTOM_DOMAIN}/{ARVAN_STATIC_LOCATION}/"
        if ARVAN_STATIC_CUSTOM_DOMAIN
        else f"{ARVAN_ENDPOINT_URL}/{ARVAN_STATIC_BUCKET}/{ARVAN_STATIC_LOCATION}/"
    )
else:
    STATIC_URL = "/static/"
    STATIC_ROOT = BASE_DIR / "static"
    MEDIA_URL = "/media/"
    MEDIA_ROOT = BASE_DIR / "media"

REST_FRAMEWORK = {
    "DEFAULT_AUTHENTICATION_CLASSES": [
        "rest_framework_simplejwt.authentication.JWTAuthentication",
    ],
    "DEFAULT_RENDERER_CLASSES": [
        "rest_framework.renderers.JSONRenderer",
    ],
    "DEFAULT_SCHEMA_CLASS": "drf_spectacular.openapi.AutoSchema",
}

SIMPLE_JWT = {
    "ACCESS_TOKEN_LIFETIME": timedelta(
        minutes=config("JWT_ACCESS_TOKEN_LIFETIME_MINUTES", default=30, cast=int)
    ),
    "REFRESH_TOKEN_LIFETIME": timedelta(
        days=config("JWT_REFRESH_TOKEN_LIFETIME_DAYS", default=30, cast=int)
    ),
    "ROTATE_REFRESH_TOKENS": True,
    "BLACKLIST_AFTER_ROTATION": True,
}

CORS_ALLOWED_ORIGINS = config("CORS_ALLOWED_ORIGINS", default="", cast=Csv())
CORS_ALLOW_ALL_ORIGINS = config("CORS_ALLOW_ALL_ORIGINS", default=False, cast=bool)
CSRF_TRUSTED_ORIGINS = config("CSRF_TRUSTED_ORIGINS", default="", cast=Csv())

CELERY_BROKER_URL = config("REDIS_URL", default="redis://localhost:6379/0")
CELERY_RESULT_BACKEND = config("REDIS_URL", default="redis://localhost:6379/0")
CELERY_ACCEPT_CONTENT = ["json"]
CELERY_TASK_SERIALIZER = "json"
CELERY_RESULT_SERIALIZER = "json"
CELERY_TIMEZONE = TIME_ZONE

KAVENEGAR_API_KEY = config("KAVENEGAR_API_KEY", default="")
KAVENEGAR_TEMPLATE = config("KAVENEGAR_TEMPLATE", default="")
KAVENEGAR_SENDER = config("KAVENEGAR_SENDER", default="")

APP_VERSION_LATEST = config("APP_VERSION_LATEST", default="1.0.0")
APP_VERSION_MIN = config("APP_VERSION_MIN", default="1.0.0")
APP_UPDATE_URL = config("APP_UPDATE_URL", default="")

