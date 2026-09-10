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

## 10 September 2026 — Google policy review before submission

Status: **do not submit**. This review found a concrete Android navigation defect and additional unresolved content-safety gates. Passing the build does not close these gates.

### Existing Play app and package conflict

The September 8 record at `../production-release-2026-09-08.md` identifies the existing app:

- Developer account: `8655510549877321474`.
- App: `4974083165054418069`; package `io.narratrace.android`.
- Internal release recorded as version code 2; version 3 remains unuploaded.
- Existing console: https://play.google.com/console/developers/8655510549877321474/app/4974083165054418069

The September 10 create-app form was opened under a different developer account, `5393523377275031204` (Expand Systems, signed in as preeti@expand.systems). Live inspection shows no apps or registered Android packages there. Opening the existing app with that identity redirects to account selection. Owner confirmation of the original Google login is pending. Two other inspected Google accounts stop at Console terms acceptance; no terms were accepted on their behalf. Do not rename the package, replace signing keys, or change Doppler to work around this error. The package is fixed in Gradle; a version-code increment would not resolve package ownership.

### Policies and code findings

| Area | Evidence and disposition |
| --- | --- |
| Payments | [Google Payments policy](https://support.google.com/googleplay/android-developer/answer/9858738?hl=en) covers digital features and cloud storage; external payment routing requires an applicable exception/program. Owner already chose independently purchased website plans for the first Android release. The web account shell supports `client=android`, but native account, legal, export, MFA, appeal, and Keepsake links omitted that marker and could reach ordinary purchasing navigation. Fixed those Android entry points. Preserve authenticated cancellation and data rights. An authenticated end-to-end traversal of the resulting browser paths is still required; do not infer worldwide compliance from absence of a native checkout button. |
| User-generated content | [Full UGC policy](https://support.google.com/googleplay/android-developer/answer/9876937?hl=en) requires terms acceptance, appropriate ongoing moderation, and in-app reporting; direct user interaction requires blocking. Android has family/Circle sharing and letters. Terms acceptance exists; generic feedback exists. No recipient/user blocking implementation was found in native client or shared backend searches. Family-owner removal is not a recipient block. Release blocker. |
| Report handling | `lib/mobileFeedback.ts` requires OPENAI_API_KEY and may reject an issue report when AI labels quoted threats, sexual material, or abusive material unsafe. That is not a dependable abuse-report path. Implement a dedicated report entry using existing private support records, with identifiers/context and optional explicit evidence; do not discard reports because they quote the abuse or because AI is unavailable. Existing validation, authentication, rate limits, and Admin access must remain. Shared backend fix applies to both smartphones. |
| AI-generated content | [Google AI policy](https://support.google.com/googleplay/android-developer/answer/13985936?hl=en) requires restricted-content prevention and in-app reporting for covered generative features. [Scope guidance](https://support.google.com/googleplay/android-developer/answer/14094294?hl=en) has limited-scope exceptions, which should not be assumed for Narratrace's generated narratives/interview features. Narrative grounding rules constrain factual invention, but do not establish restricted-content controls. Verify the complete generation paths and implement/report missing safeguards before submission. |
| Data and deletion | [User Data policy](https://support.google.com/googleplay/android-developer/answer/10144311?hl=en) and [account deletion requirements](https://support.google.com/googleplay/android-developer/answer/13327111?hl=en) require in-app and external deletion paths, associated-data deletion, and disclosed retention. Native closure/status/reopen UI exists with a stated 30-day recovery window. Do not equate account restriction with completed erasure. Verify purge/provider-retry evidence and the public deletion page. September 8 Play accepted `https://www.narratrace.io/privacy#section-9`; the earlier marketing-domain URL returned 403 to Google. |
| Permissions | [Sensitive permissions policy](https://support.google.com/googleplay/android-developer/answer/16558241?hl=en) favors limited system pickers over broad photo/video access. Main manifest requests Internet, camera, microphone, and notifications; no contacts, location, broad media/storage, accessibility service, or advertising-ID permission. Capture/picker actions are user initiated. Final merged manifest, SDK behavior, physical-device permission denial/retry and background capture need final acceptance. |
| Data safety and review access | Existing draft inventory and reviewer instructions are recorded in the September 8 handoff. Reconcile those drafts against actual Firebase Messaging/device IDs, user-supplied media/profile/people data, AI processing and deletion behavior. [Google reviewer-access requirements](https://support.google.com/googleplay/android-developer/answer/15748846?hl=en) require reusable working access to restricted features. Earlier owner Google sign-in success does not verify Google reviewers can complete sign-in independently. Do not record credentials in this file. |
| Target API and signing | Current targetSdk 36 meets the [current target API requirement](https://support.google.com/googleplay/android-developer/answer/11926878?hl=en). [Play App Signing terms](https://play.google/play-app-signing-terms/) describe Google-held signing and AAB generation; no new key is needed for the existing app. Reconcile the existing Play signing certificate with OAuth and assetlinks before upload. Prior artifact checks found 16-KB-aligned native ELF load segments; device validation remains distinct. |
| Store declarations | Public brand Narratrace; legal entity Expand Systems LLC. Adult audience, no ads, non-government, non-financial and non-health declarations were previously drafted. Recheck actual listing, screenshots, content rating and access instructions in the existing app. Do not finalize draft answers merely because the new-app declaration checkboxes were checked. |
| Export and registration | Reviewed Google's [export guidance](https://support.google.com/googleplay/android-developer/answer/113770?hl=en); it does not itself determine encryption classification. App uses platform encryption and TLS; no cryptographic feature change is indicated by accepting the declaration. Owner accepted export declaration. Verify the existing app's Android developer registration before the September 30 deadline shown by Console. |

### Concrete proposed blocking implementation — awaiting resource approval

Use the existing customer identity model with one shared `user_blocks` relation: blocker account, blocked account, creation time, unique directed pair, and self-block prevention. No duplicated email identities. Only the blocker may list/create/remove their blocks; enforce authentication, authorization and rate limits on the server. A block must prevent new direct invitations/deliveries and access to blocked shared content, with checks at creation, eventual scheduled dispatch, and read time. Unblocking must not automatically restore revoked access. Reconcile closure/erasure and existing audit-retention rules. Cover web, Android, and iOS against the same additive API contract; older clients must remain protected by server enforcement. No table or new persistent resource has been created.

Reports should use existing `support_requests`/`support_messages`, with a visible Report content/user action and explicit context. Admin triage remains in the existing support workflow. Avoid automatically copying private archives into reports. No new moderation service is proposed. Complete a separate review of generated-content controls before release.

### Scoped verification for the Android navigation correction

- 181 Android unit tests passed, zero failures/errors; `lintDebug` passed. Updated existing closure/legal-link regression expectations.
- Doppler-backed `verifyStoreRelease bundleRelease --offline --no-configuration-cache` passed. Version remains 3; no Doppler variable changed.
- AAB SHA-256: `226fe46e761d77e8cc64b59d2324b77522bd0cf4465428ecfba83ca851b0e179`; no Markdown entries in the bundle.
- Logs: `/private/tmp/narratrace-play-policy-checks.log`, `/private/tmp/narratrace-play-policy-bundle.log`.
- Security gate for this correction: HTTPS destinations retained; existing narrow appeal allowlist retained; query context is presentation only, not authorization; existing auth/entitlement/closure controls unchanged; no credentials, permissions or dependencies added. Appeal query context is replaced without dropping other parameters.
- Code Quality gate for this correction: tests, lint, signed build and diff whitespace checks pass. Replaced obsolete unrestricted routes; no parallel routing implementation added.
- Impact: existing web Android mode is reused; no web edit. Android-specific purchase-navigation context does not belong on iOS URLs; no shared API change from this correction. Content reporting/blocking remains a shared web/iOS/Android gap. Admin and QA/OPS need report-triage and abuse-regression reconciliation. Marketing/Expand Systems have no public-fact change from the navigation correction. No agents activated.
- Overall release Security and Code Quality gates remain OPEN pending the content-safety implementation, authenticated navigation/device verification, and final store review. This bundle is not uploaded or submitted.
