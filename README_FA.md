# HomeOS TV Launcher 0.2.0

لانچر سبک Android TV با رابط فارسی و کنترل D-pad.

## سازگاری
- حداقل Android 6 / API 23؛ هدف Android 9 / API 28.
- روی Android 9 (API 28) و نسخه‌های بالاتر اجرا می‌شود.
- بدون کتابخانه JNI/NDK؛ بنابراین APK به ABI خاص MediaTek/ARM32/ARM64 وابسته نیست.
- Java 11 source compatibility، Android Gradle Plugin 8.7.3، Gradle 8.9، JDK 17.

## اتصال پیش‌فرض فعلی
- Host: `192.168.254.54`
- Port: `8081`
- Path: `/health`
- URL: `http://192.168.254.54:8081/health`

در تنظیمات، IP/Host، Port و Path جداگانه وارد می‌شوند. برنامه URL را می‌سازد، درخواست GET را خارج از Main Thread اجرا می‌کند، HTTP status و JSON را می‌خواند و فقط در صورت `status: ok` وضعیت «متصل و تأییدشده» نشان می‌دهد. هر ۱۵ ثانیه Health check تکرار می‌شود.

## امنیت
این نسخه برای شبکه محلی فعلی از HTTP استفاده می‌کند؛ Android cleartext برای این APK فعال است. HTTP رمزنگاری یا احراز هویت فراهم نمی‌کند؛ برای دسترسی اینترنتی/خارج از LAN باید HTTPS و احراز هویت اضافه شود. هیچ کلید یا رمز عبوری در APK ذخیره نشده است.

## Build
با JDK 17 و Android SDK Platform 35:

```bash
gradle --no-daemon assembleDebug
```

خروجی: `app/build/outputs/apk/debug/app-debug.apk`

GitHub Actions با اجرای دستی یا push به main همین APK را تولید و به‌صورت artifact نگهداری می‌کند.

## نصب روی نسخه قبلی
Package ID حفظ شده (`com.homeos.tv`) و versionCode افزایش یافته است؛ در صورت یکسان بودن signing key می‌توان نسخه جدید را روی قبلی نصب کرد. اگر امضای نسخه قبلی متفاوت باشد، Android نصب روی نسخه قبلی را رد می‌کند؛ در آن حالت ابتدا باید از تنظیمات نسخه قبلی نسخه پشتیبان گرفت و سپس uninstall/install انجام داد.
