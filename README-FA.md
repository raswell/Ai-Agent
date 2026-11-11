# بسته ساخت APK لانچر HomeOS TV — نسخه 0.1.0

## دستگاه هدف
- سازنده/مدل: Snowa SQLD-65SA850U
- Android TV: نسخه 9 (API 28؛ طبق اعلام مالک دستگاه)
- RAM: 1.5 GB DDR3
- حافظه داخلی: 16 GB eMMC
- نسخه نرم‌افزار تلویزیون: V2.2.0
- چیپست: MediaTek؛ مدل دقیق و ABI هنوز استخراج نشده است.

## وضعیت تحویل
این ZIP شامل سورس کامل ماژول Android و اسکریپت ساخت است؛ **خود APK کامپایل‌شده داخل ZIP نیست**. محیط آماده‌سازی فاقد Android SDK و Gradle بود و ساخت APK در این محیط ممکن نشد. هیچ باینری ساختگی یا تأییدنشده‌ای به‌عنوان APK قرار داده نشده است.

## ابزارهای لازم برای Build
- JDK 17
- Android SDK Platform 35 و Build Tools 35.x
- Gradle 8.9
- دسترسی شبکه برای دریافت وابستگی AndroidX WebKit 1.12.1 و پلاگین‌های Gradle، مگر اینکه از قبل cache شده باشند.

نسخه Android 9 با minSdk=23 در سطح حداقل نسخه پشتیبانی می‌شود. compileSdk=35 برای زمان ساخت است و به معنی نیاز تلویزیون به Android 15 نیست.

## ساخت APK
در Android Studio پوشه `android/` را باز و منتظر Sync بمانید؛ سپس از مسیر Build > Build APK(s) استفاده کنید.

یا در محیطی که Gradle 8.9 و SDK تنظیم شده:

```bash
cd android
gradle --no-daemon assembleDebug
```

خروجی:
`android/app/build/outputs/apk/debug/app-debug.apk`

## نصب و تست روی تلویزیون
پس از فعال‌کردن Developer options و USB debugging و اتصال ADB:

```bash
adb install -r android/app/build/outputs/apk/debug/app-debug.apk
```

سپس Home را فشار دهید و در صورت ارائه گزینه، HomeOS TV را انتخاب کنید. امکان جایگزینی لانچر اصلی به سیاست Firmware اسنوا وابسته است و هنوز روی SQLD-65SA850U آزمایش نشده.

## ملاحظات سخت‌افزاری
- طراحی با یک WebView فعال و رابط Native انجام شده است.
- رفتار روان و مصرف RAM هنوز روی تلویزیون واقعی اندازه‌گیری نشده است.
- مدل دقیق SoC و معماری CPU معلوم نیست؛ قبل از بهینه‌سازی ABI خروجی ADB زیر را بگیرید:

```bash
adb shell getprop ro.product.cpu.abi
adb shell getprop ro.product.cpu.abilist
adb shell getprop ro.board.platform
```

## اعتبارسنجی سورس
اعتبارسنجی ساختار و API در بسته Backend/پروژه کامل انجام شده است. این تست‌ها جایگزین Build اندروید یا تست روی تلویزیون نیستند.
