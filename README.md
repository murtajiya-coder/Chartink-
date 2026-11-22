# Chartink + Screener (Android)

One Kotlin / Jetpack Compose app hosting two independent WebViews:
- Chartink: https://chartink.com/dashboard/488230
- Screener.in: https://www.screener.in/

## Build the APK
Android Studio: open this folder, let Gradle sync, then Build > Build APK(s).
Command line (JDK 17 + Android SDK installed):

    ./gradlew assembleRelease     # app/build/outputs/apk/release/app-release.apk (minified, debug-signed, installable)
    ./gradlew assembleDebug       # app/build/outputs/apk/debug/app-debug.apk

No SDK locally? Push this folder to a GitHub repo: .github/workflows/build.yml builds both APKs
and attaches them to the workflow run (Actions > Build APK > Artifacts).

## Notes
- Sessions: each site has its own WebView; cookies are per-domain, so logins never mix.
- Tab switching only toggles visibility, so pages are never reloaded.
- Autofill: enable a service in Android Settings > Passwords & autofill (Google, Bitwarden, etc.).
- The bottom-bar icons are simple vector drawables (res/drawable/ic_chartink.xml, ic_screener.xml); swap in official logos if you like.
- Before publishing to Play Store, replace the debug signing config in app/build.gradle.kts with your own keystore.
