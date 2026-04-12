package ir.siaheh.ui.common

data class FriendlyErrorUi(
    val title: String,
    val message: String,
)

object FriendlyErrorMapper {
    fun map(raw: String?): FriendlyErrorUi {
        val text = (raw ?: "").lowercase()

        return when {
            text.contains("unable to resolve") || text.contains("failed to connect to") || text.contains("unknownhostexception") -> FriendlyErrorUi(
                title = "اتصال ناپایدار شد",
                message = "فعلا مسیر اینترنت ناپایداره.\nاگر می‌تونی اینترنتت رو عوض کن و دوباره امتحان کن.",
            )
            text.contains("timeout") || text.contains("timed out") || text.contains("sockettimeout") -> FriendlyErrorUi(
                title = "کمی طول کشید",
                message = "پاسخ کمی دیر رسید.\nیه بار دیگه امتحان کنیم؟",
            )
            text.contains("503") || text.contains("502") || text.contains("500") || text.contains("service unavailable") -> FriendlyErrorUi(
                title = "یه وقفه کوتاه داریم",
                message = "احتمالا یه بخش از سرویس رو به‌روزرسانی می‌کنیم.\nچند دقیقه دیگه دوباره امتحان کن.",
            )
            text.contains("401") || text.contains("unauthorized") || text.contains("invalid token") -> FriendlyErrorUi(
                title = "نیاز به ورود دوباره",
                message = "برای ادامه لازمه یک‌بار دوباره وارد حساب بشی.",
            )
            else -> FriendlyErrorUi(
                title = "یه مشکل موقت پیش اومد",
                message = "نگران نباش، معمولا با یک تلاش دوباره حل میشه.\nاگر ادامه داشت به پشتیبانی پیام بده تا سریع پیگیری کنیم.",
            )
        }
    }
}
