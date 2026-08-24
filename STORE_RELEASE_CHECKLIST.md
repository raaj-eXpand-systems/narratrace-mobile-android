# Narratrace Android store release checklist

Production releases are signed and configured only through Doppler-provided environment variables. Never commit a keystore, password, Firebase configuration, or production credential.

## Required Doppler variables

- `NARRATRACE_API_BASE_URL`
- `NARRATRACE_GOOGLE_SERVER_CLIENT_ID`
- `NARRATRACE_FIREBASE_API_KEY`
- `NARRATRACE_FIREBASE_APPLICATION_ID`
- `NARRATRACE_FIREBASE_PROJECT_ID`
- `NARRATRACE_FIREBASE_SENDER_ID`
- `NARRATRACE_ANDROID_KEYSTORE_PATH`
- `NARRATRACE_ANDROID_KEYSTORE_PASSWORD`
- `NARRATRACE_ANDROID_KEY_ALIAS`
- `NARRATRACE_ANDROID_KEY_PASSWORD`
- `NARRATRACE_ANDROID_VERSION_CODE`
- `NARRATRACE_ANDROID_VERSION_NAME`

Run the production gate and bundle through Doppler:

```bash
doppler run -- ./gradlew verifyStoreRelease bundleRelease
```

## Before internal testing

- Verify Experiment A still requires trial or paid-plan activation before any capture flow opens.
- Verify Experiment B permits exactly one guided interview before activation while written,
  photo, standalone audio/video, and Letters entry points remain locked.
- Complete the Experiment B interview and verify the app presents the secure plan-selection
  handoff instead of reopening the introductory interview.

- Confirm the bundle is signed with the intended upload certificate.
- Register its SHA-256 fingerprint with Google OAuth and `https://www.narratrace.io/.well-known/assetlinks.json`.
- Verify Google sign-in, optional MFA, token rotation, session revocation, and inactivity locking.
- Test microphone, camera, photo picker, encrypted staging, 2 GB video boundaries, interrupted TUS resume, playback, and background reconciliation on a physical Android device.
- Test family and Circle invitation links from email, including signed-out handoff, acceptance, decline, and revoked invitations.
- Test send-now and future delivery across daylight-saving boundaries and a non-US timezone.
- Run TalkBack, font scaling at 200%, display scaling, RTL, keyboard-only navigation, reduced motion, and light/dark themes.
- Confirm notifications contain no protected content and Activity remains authoritative.
- Verify account archive, billing, closure, and recovery open only the authenticated Narratrace website.

## Store submission

- Increment `NARRATRACE_ANDROID_VERSION_CODE`; never reuse a Play version code.
- Review the Data safety form against actual capture, upload, analytics, and notification behavior.
- Provide privacy-policy and account-deletion URLs.
- Upload first to an internal testing track and complete the device matrix before promotion.
- Retain mapping and native-symbol outputs for the exact released bundle.
