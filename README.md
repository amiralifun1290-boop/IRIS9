# IRIS — دستیار هوش مصنوعی اندروید

## ساختار پروژه
- ۱۸ قابلیت آفلاین
- Jetpack Compose UI
- Room Database
- CameraX
- ML Kit (OCR, Object Detection, Translation)
- Accessibility Service
- Floating Overlay
- Voice Wake Service

## پیش‌نیازها
- JDK 17
- Android SDK 34
- Gradle 8.6

## ساخت APK
```bash
./gradlew assembleDebug
./gradlew assembleRelease
```

## تنظیم Signing
متغیرهای محیطی:
- KEYSTORE_PATH
- KEYSTORE_PASSWORD
- KEY_ALIAS
- KEY_PASSWORD

## Cloud Build
- Codemagic: فایل `codemagic.yaml`
- GitHub Actions: فایل `.github/workflows/build.yml`
