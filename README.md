# Zone Alerts Android

Open this folder in VS Code. Requires JDK 17 and Android SDK 34 for local builds.

GitHub Actions builds the debug APK on push. To build locally with installed Gradle 8.9:

```powershell
gradle assembleDebug --no-daemon
```

App: com.zonealerts, minSdk 26, targetSdk 34.

Monitoring runs on the Android phone, not a cloud server. It polls Yahoo Finance during weekday 09:15–15:30 IST; market holidays are not handled. Yahoo quotes may be stale or blocked. The app does not guarantee uninterrupted background monitoring or reboot restart on newer Android versions.

Telegram token is saved in app SharedPreferences; avoid using this for high-security credentials.
