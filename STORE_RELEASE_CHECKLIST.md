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

- Verify the server-authorized trial permits one guided interview and gates other product workspaces.
- Complete the complimentary interview and verify existing-story access remains available while creation requires eligible account access.
- Verify the owner-approved free-companion notice offers Open trial interview/Open existing story and Refresh access, without purchase links or website purchasing directions.

- Confirm the bundle is signed with the intended upload certificate.
- Register its SHA-256 fingerprint with Google OAuth and `https://www.narratrace.io/.well-known/assetlinks.json`.
- Verify Google sign-in, optional MFA, token rotation, session revocation, ordinary idle-return continuity, and fresh verification for sensitive operations.
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

Report-ingestion follow-up: web main `0d03d3d36353c267ef33023ff7b0935bc40c269e` now preserves issue evidence without requiring AI or applying the ordinary-feedback content/repetition filter. Shared mobile API covers Android and iOS; existing authentication, limits, attachment validation and private support workflow remain. 353 suites/2,993 tests, typecheck, lint and production build pass. See `../narratrace-app/docs/google-play-issue-report-fix-2026-09-10.md`. Production verification is pending at this checkpoint. This closes the identified ingestion defect, not the remaining report discoverability, user blocking, generation-safety or Play submission gates.


## Reception landing — 19 September 2026

Owner-approved Home overview synchronized with iOS: Stories, Wall / Mosaic, Media, People, Capture and More explain their purpose and open only on selection. More retains Letters, Family, Keepsake book and settings. Removed the automatic purpose questionnaire/capture redirect; authenticated shell starts Home for new and returning customers. Existing presentation-only pending preference is cleared for upgrade compatibility. Future feature communications are intended for Home; no new communications service or store upload is included. iOS menu changes are deferred at owner request.

Security gate PASS locally: account/legal/lifecycle gates, installation-bound sessions, PKCE, protected storage and server authorization unchanged. No new dependencies or environment edits; configured builds consume existing Doppler configuration without logging values. Code Quality gate PASS locally: 186 unit tests, eight onboarding/accessibility/navigation instrumented tests, lint, debug and release builds, configured signed release. iOS Release build and 112 signed regression tests pass. No bundled Markdown or whitespace errors. Removed obsolete questionnaire and empty-memory capture CTA; Home points into existing workspaces. Logs /tmp/narratrace-android-reception-{build,ui,configured-release}.log.

Impacts: no server/API shape, Admin, marketing, Expand Systems, billing or delivery changes. QA/operations should verify fresh/returning sign-in, Home destination links, explicit capture, themes and existing invitation review. No agents activated. Updated existing release installation in place and launched; it is at secure sign-in, so authenticated Reception visual acceptance remains owner-assisted and pending. No app-store submission. Shared web recovery prerequisite is live on production main 2722c0b and verified in Chrome; successful native browser dismissal still needs runtime acceptance.

19 September card-icon correction: owner screenshot exposed omitted Android Reception icons. Reused ArchiveNavigationIcon above every destination title, preserving the existing Daylight-only artwork and theme-colored symbols used by menus/bottom navigation. iOS already has equivalent icons; no iOS code change required. Security gate: presentation-only, no auth/API/storage/permission changes. Code Quality gate: configured signed Release build and lint pass; whitespace check passes. Existing navigation actions unchanged, no new dependencies or duplicate icon mapping. Log /tmp/narratrace-android-reception-icons.log. Admin, marketing, Expand Systems and server contracts unaffected; QA impact is visual confirmation of the six card icons and theme consistency. No agents or store upload.


## 20 September 2026 — owner-authorized Android parity implementation

Baseline: production customer web main `2722c0bbac303f70737dfbd175fee32ce149a0cb`, verified by the primary agent. Existing uncommitted session/trial fixes preserved. Owner explicitly selected the same free-companion restriction notice on both native platforms: no purchasing CTA or website purchase directions. Existing account/privacy/recovery administrative links retain `client=android`.

Implemented client outcomes: completed-trial creation gating using server account capabilities and precise legacy/stable access-error classification; paid local capture can reuse same-session verified eligibility only after transport failure; trial gate refresh and same-account token rotation preserve mounted workspace state. Account identity switches/sign-out evict cached eligibility and the authenticated subtree. Revocation, creation/status/deletion and Family/Circle/invitation mutations report failure instead of implying success. Public link status is verified before showing create/revoke actions.

Letter validation errors no longer become offline-save success. Encrypted device drafts retain recipient, self/Circle/member choices, date/time/timezone and retry identity; all recovered drafts require explicit review and are reachable from Letters. Legacy text-only synchronization cannot silently discard delivery intent. Circle targeting consumes the existing owner/active-member API contract. Typed delivery-contact failures expose administrative verification recovery. Letter detail retry issues a fresh load.

Interview originals now have authenticated audio/video playback; Letter details support authorized voice playback and owner recording/attachment with retry. Voice recording is attached only to an already saved Letter, with explicit notice that an earlier delivery is not recalled or resent. **Shared gap:** initial create-and-send plus audio is not atomic in the existing API/web flow; this work does not promise a recording was included in an already-sent delivery. No backend contract/table change was made.

People-linked resources and archive search now open authorized detail routes; historic numeric media identifiers decode compatibly. Content-report actions on interview, media, Letter and Circle surfaces use the existing private issue workflow, sending only explicitly disclosed identifiers and user-entered explanation, without automatic protected-content attachments.

Security review: server authorization, account restrictions/purge, session expiry/rotation/revocation and deletion step-up remain authoritative. Cached eligibility is scoped to current credentials and permits presentation/local staging only, never server authorization. Original audio stays in memory with bounded authenticated retrieval, no redirects, and background/disposal release. Video accepts HTTPS Cloudflare Stream manifest URLs only. Local Letter recordings stay in app-private recorder storage until stopped, then memory for explicit upload; no new dependency or permission. Store upload and physical installation are outside this specialist's scope.

Verification results are appended after final frozen-source gates. Authenticated camera/microphone/provider playback, real-account delivery and store-console/reviewer acceptance remain distinct from fixture and emulator checks. QA/OPS impact is the new failure-recovery, draft review, restriction and reporting coverage; no persistent operational resources were created. Shared public catalog, Admin roles, marketing and Expand Systems facts are unchanged. Partner remains web-only.

Account isolation follow-up in this workstream: protected media and device drafts now carry the authenticated owner ID, and production stores require the matching current account before listing, reading, updating or reconciling records. Sign-out hides records; another account cannot open/upload them; owner-scoped purge preserves other owners. Existing unbound encrypted records are quarantined in place without automatic attribution or content display. **Recovery limitation:** legacy unbound drafts/captures need a separately verified recovery process; do not silently assign them to whichever account signs in next.

Offline scope is precise: verified paid customers retain local work across foreground transport failures within the verified session. Cold launch still requires initial runtime/lifecycle checks; eligibility is not persisted, so offline cold-start capture/draft access waits for reconnection. Unknown/trial sessions cannot use the runtime-blocked offline-capture fallback. This is not a claim of complete cold-offline feature parity.

Intermediate gate evidence before final account-switch race hardening: 189 unit tests pass with zero failures/errors/skips, including account-switch/sign-out isolation for drafts and queued media, verified-session paid/offline capture behavior, numeric search identifiers, completed-trial and legacy/stable access failure handling, encrypted delivery-intent retention and video URL trust boundaries. Debug build and lint pass; lint reports zero errors, 39 warnings and 17 hints. All nine Android 16 Pixel_8 emulator instrumentation tests pass, including completed-trial notice, existing-story/refresh actions and absence of purchasing CTA. The first emulator run stalled; a cold start using software graphics restored adb and the complete suite passed. Logs: `/tmp/narratrace-android-parity-isolation-gates.log`, `/tmp/narratrace-android-parity-ui.log`. These tests use fixtures/presentation; they do not certify live-account provider capture/playback or Play reviewer access.

Intermediate signed candidate (superseded by final race-hardening candidate below): existing Doppler `narratrace/prd_narratrace-prod` configuration passed `verifyStoreRelease bundleRelease --offline --no-configuration-cache` on Java 17. `jarsigner -verify` reports `jar verified`. Candidate: `app/build/outputs/bundle/release/app-release.aab`, 16,424,655 bytes; SHA-256 `5d42db7eff4dc3e084644fd63de1d09861d1dc56d51a0c0308badce7e3b638d4`. Debug APK and signed AAB contain no Markdown or SQL files. No secrets were printed, no variables changed, no bundle uploaded and no physical installation performed. Build log `/tmp/narratrace-android-parity-signed-bundle.log`; signature log `/tmp/narratrace-android-parity-signature.log`.

Security and Code Quality gates PASS for the scoped frozen client source and automated fixture/emulator coverage, with the explicit cold-offline, unbound-legacy recovery, atomic initial voice-delivery and live-provider acceptance limits above. Store submission readiness still depends on the primary agent's integrated policy/signing/reviewer/device review; this local result does not close those external gates.

Final isolation review additionally bound every encrypted-store operation to one immutable owner snapshot, including purge merge/write decisions. Reconciliation verifies the exact account/token lease after refresh and before media reads, authorization, transfer chunks, confirmation and removal. Session transitions invalidate in-flight refresh results; neither delayed success nor rejection may restore, overwrite or clear a newer session. Stale callers receive no replacement account credential. Known concurrent rotation remains single-flight; unknown rejected tokens and queued requests crossing an account transition cannot borrow the new session. Regression fixtures exercise owner change during encrypted purge, exact upload credentials, delayed signout/adoption/rejection, old-account rejection and queued account adoption.

Final superseding gate evidence after isolation/refresh hardening: **197 unit tests, zero failures/errors/skips; 9/9 Android 16 Pixel_8 emulator tests; lint zero errors (39 warnings, 17 hints); debug build and signed release bundle all PASS.** Existing Doppler configuration passed `verifyStoreRelease bundleRelease --offline --no-configuration-cache`; `jarsigner -verify` reports `jar verified`. Final candidate `app/build/outputs/bundle/release/app-release.aab`: **16,429,142 bytes**, SHA-256 **`6f1b2d96deabb3e4a6d1c8bbaba79a67f586c543e0511e94c7a9d7c0cfddab75`**. APK/AAB contain zero Markdown/SQL files. Logs: `/tmp/narratrace-android-parity-final-race-gates.log`, `/tmp/narratrace-android-parity-final-signed-bundle.log`, `/tmp/narratrace-android-parity-final-signature.log`. Earlier 189-test candidate remains documented as intermediate evidence only. Scoped Security and Code Quality gates pass with the explicit residual limitations above. No upload, commit, customer mutation or physical installation.

### Existing Play account and signing reconciliation — 20 September 2026

Owner switched the dedicated Chrome session into existing developer account 8655510549877321474, app 4974083165054418069, io.narratrace.android. Registration requirements are complete. Latest uploaded bundle is version 2 (1.0.0), active internal testing; version 3 remains available for this candidate. The final candidate upload-certificate SHA-256 matches the existing Play upload key: `34:85:51:E1:72:7E:CD:CF:78:E3:7A:77:4F:B2:C3:F7:6F:79:51:43:A4:D2:10:F3:6B:F8:39:C0:38:C1:4C:08`. Dedicated Chrome also verified production assetlinks includes the Play app-signing and upload fingerprints. No key rotation or new listing was created.

Play setup is 8/11 complete: Content rating, Data safety and Store listing remain incomplete. Internal tester configuration, independent reviewer admission, real-device acceptance and genuine final screenshots remain release checks. Production is inactive. No new bundle uploaded or submitted.


## 21 September 2026 — question speech implementation (local)

OpenAI question read-aloud is implemented locally against the shared protected speech endpoint. See `../narratrace-question-tts-handoff-2026-09-21.md` for exact behavior, tests, cost-accounting SQL prerequisite and release limits; see `../narratrace-store-screenshot-plan-2026-09-21.md` for the six-screen capture plan. Nothing uploaded/submitted. The 20 September submission packages do not contain this code. Complete owner SQL verification, production-main service release and native runtime acceptance, then regenerate the store package. Manual release remains unchanged.

## Verified cleanup candidate — 23 September 2026

Pending Nia signature styling and protected question playback are included in the verified candidate. Both native clients use the released saved-message speech contract and preserve Marin. Security gate PASS for scoped changes: HTTPS/bearer authorization, no redirects or persistent speech cache, bounded MP3 playback, no automatic synthesis retry, account isolation and stale/background playback cancellation. No new permissions, dependencies, purchase links or server rule changes.

Code Quality gate PASS: 202 unit tests, zero failures/errors/skips; lintDebug and assembleDebug; configured verifyStoreRelease, bundleRelease and assembleRelease. AAB signature verification reports jar verified. AAB and APK contain zero Markdown/MDX/SQL files. Version 1.0.0 (3). AAB SHA-256: 67756467ba90e69be1a35991798efe47f449cdb65ee3649a2ff60109e67f5575. APK SHA-256: 4c6e0d65dcfc711fd15da9faab30d3f1057bc40065104c675e0e246e734e8157. Existing build output paths hold the new candidate, superseding earlier binaries.

iOS verification: 135 tests and signed App Store export pass for the same contract. No additional Admin, marketing or public-company fact change. QA follow-up remains physical audio routing/interruption and independent reviewer acceptance. No agents activated. Store screenshots, declarations, reviewer access and device acceptance remain open; source commit/push is not a Play upload or submission. Owner will verify web production manually.

### Native screenshot-session transport corrections — 23 September 2026

Authenticated store-capture work exposed two contract defects. iOS interview pagination embedded `?limit=100` in a path component; it now supplies URL query items and preserves cursor values. Both clients rejected the signed storage endpoint returned by the server: the Supabase client uses `/storage/v1/object/upload/sign/Uploads/`, including the `object` segment. The native validators now recognize that endpoint. Android also requires the private Uploads bucket, exactly one nonempty token, and no URL fragment, matching iOS. HTTPS, approved storage host suffix, credentials/port restrictions, redirect refusal, bearer session checks, and account-bound encrypted-queue ownership remain enforced. No backend, schema, environment, purchase flow, or permissions changed.

Primary impact assessment: iOS and Android upload fixes are synchronized. Android interview query construction already uses the correct endpoint. Customer web, marketing, Admin, and Expand Systems require no source changes because their contracts and public capabilities are unchanged. QA/operations impact is release evidence plus real-device interview loading and photo upload checks; no QA database work or specialist agents activated.

Scoped Security gate: PASS by primary source review of signed-upload boundaries and unchanged redirect/session/queue ownership protections. Code Quality gate: PASS, 203 unit tests with zero failures/errors/skips and lintDebug. Existing Doppler-backed verifyStoreRelease, bundleRelease and assembleRelease passed; version remains 1.0.0 (3). AAB signature verified; AAB and APK contain no Markdown/MDX/SQL. Replacement AAB SHA-256: `3ece1e85a038b95a5443aef5ed3ccfcd874a3ea441b2bf8739639502337de259`; APK: `f03c75a5045d05b9d51e1c8b2100454813944b94a5be9cc1e5c3cb10b017fbf2`. These supersede the earlier 23 September binaries. Live synthetic-photo verification and final screenshot set remain tracked in the root capture handoff. No store upload or submission.

The same capture session exposed a third native contract mismatch: recording capacity required a `remainingLabel` string absent from the server response. Both clients now decode the required numeric byte/audio/video limits and derive only their display label locally. Missing numeric limits still fail decoding; no entitlement or recording limit is invented. New tests mirror the actual numeric-only server response. The earlier transport-only package hashes above are intermediate and superseded by the final capacity-corrected candidates below.

Final capacity-corrected candidate: 204 tests pass, zero failures/errors/skips; lintDebug, verifyStoreRelease, bundleRelease, and assembleRelease pass. Signature verified and zero Markdown/MDX/SQL in both packages. AAB SHA-256 `eafb35d96ca0e896b29842c41dfbc66eb4017a279709de95b865b890529bad7d`; APK SHA-256 `64e2026be39ab1f1497f7182e7687057d465d08fadc3728aa0c63548da0454fa`. Real review-account evidence: the approved sample photo successfully moved from encrypted pending upload to preserved Media after the endpoint correction, and its demonstration caption was saved. No existing photo or regression interview was changed.

Final consent-corrected candidate supersedes the preceding hashes: an explicit narrative-confirmation tap previously serialized to an empty body because the true Boolean was a default value. The consent field is now required and a wire-payload regression asserts that true is transmitted. iOS already sends its required Boolean explicitly; no iOS/server change is needed for this finding. Final evidence: 205 unit tests pass with no failures/errors/skips; lintDebug and all signed-release tasks pass. AAB signature verified; no Markdown/MDX/SQL in AAB/APK. Final AAB SHA-256 `8fd69184cac19c8f7bcc568fe9b876e1b68caea9680cf5d24e5ba349c81d4831`; APK `858cc46cc8085545daef0db2fdebdc550c71f0eeabc82867aee2238a97df701f`. Version remains 1.0.0 (3). No upload or store submission.
