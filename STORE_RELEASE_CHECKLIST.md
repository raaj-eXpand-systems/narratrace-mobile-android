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


## 10 September 2026 — integrated check-in verification

Owner explicitly included all existing Android and iOS uncommitted work in this check-in. This record covers native source integration, not Play submission or physical-device certification.

Security gate: reviewed fresh explicit-sign-in installation binding and encrypted pending PKCE transaction retention; callback state, HTTPS destination, token/session expiry and server authorization remain enforced. Signed media accepts only HTTPS Supabase signed-object or signed-render paths; tests cover rejected transport and host forms. Onboarding preferences control presentation only. Capture remains behind authentication and server eligibility. No credentials, new dependency, backend resource or executable downloaded policy added. Existing secure storage and protected networking regressions pass.

Code Quality gate: 181 unit tests pass with build cache disabled and tasks rerun; lintDebug, assembleDebug and assembleRelease pass. Lint reports zero errors, 21 warnings and 16 hints (SDK compatibility advice, style/deprecation and image resource placement); no fatal issue. Old cached Kotlin interface bytecode initially caused four errors, resolved by a fresh test compilation, not by changing authentication behavior. Onboarding emulator results are recorded below after completion. Store checklist above remains applicable.

Cross-platform reconciliation: both apps retain logo/tagline before photographic welcome, uncropped images, introduction before new-user flow, sign-in before capture, shared bottom/menu destination names, Daylight-only artwork, System/Light/Dark plus More themes, fresh account-switch binding, and a clearly labelled Library illustration hidden until photo availability is verified and hidden when a customer photo exists. The example photo matches the authoritative web asset. Server contracts remain additive and backward-compatible; Partner remains web-only. iOS signed simulator suite passes all 105 tests. QA/OPS follow-up includes authenticated device sign-in, capture and TalkBack/VoiceOver acceptance; no specialist agents activated or store release claimed.

Final Pixel 8 / Android 16 emulator run: all seven onboarding tests pass, including launch logo ordering, new/returning journeys, skip, purpose selection, recipient Wall navigation and readable Library sample. The prior recipient assertion expected Library; reconciled it to Wall, matching both native implementations. Debug and Release APKs contain no Markdown. Security and Code Quality gates pass for source check-in; authenticated physical-device and store acceptance remain separate.
