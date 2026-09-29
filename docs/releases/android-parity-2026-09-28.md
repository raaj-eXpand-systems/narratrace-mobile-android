# Android resubmission, 28 September 2026

Target: existing io.narratrace.android, version 1.0.0 (4), current main, matching the released shared contracts and iOS 1.0.0 (36). Owner authorized Android parity and Google Play resubmission. No agents activated. Owner chose emulator checks; physical Android and Bluetooth hardware acceptance remain unverified.

## Release contents and parity assessment

- Prior Android main already includes named blocked people, red Blocked status, status/name sorting, direct unblock actions, and explicit successful block/unblock copy. These changes were not in Play build 3.
- Restored the exact approved iOS red Na / green-gradient icon as a separate launcher asset. Preserved the existing in-app wordmark.
- Native delivery-email review, challenge and verification use the existing versioned account endpoint. Changing the entered address invalidates the pending challenge. Verification never changes sign-in or the Letter recipient. Composer state remains mounted while verification is open.
- Private Letter drafts save before delivery validation. Explicit private saving also syncs text with the existing offline-draft endpoint; recipient/delivery choices remain encrypted locally and require review. Drafts can be opened from Letters and a person's private draft disclosure group.
- Individual Letter reports use the dedicated versioned endpoint. Reported Letters remain readable but all owner mutations are hidden until the hold is resolved. Immediate local hold covers the period before reload.
- Fresh per-resource deletion verification now applies to Letters, media, interviews, responses, Circles and account closure. Email/authenticator method comes from the server. Proof is bounded to the selected account/session/resource; cancellation makes no destructive request. Incorrect codes can be retried without signing out. Existing owner and server eligibility enforcement remain authoritative.
- Transcript correction and explicit red response removal actions, with confirmation and verification. Media and interview playback use protected bounded retrieval and release on background/disposal. Playback does not resume from a superseded load.
- Integrated pre-existing, previously uncommitted recorder interruption fixes and their tests: background, focus, microphone privacy, route loss and duration limit finalize/preserve valid takes without automatic restart.
- Relationship map supports zoom controls, scrolling, collapsible branches, incremental expansion, name ordering, person navigation and accessible list alternative. Android uses native scroll/zoom controls instead of the iOS UIScrollView implementation.
- Narratrace Blue remains default. Theme selection now updates mounted Compose views without Activity recreation. Compact account capabilities, recovery-screen sign-out, interview mode-selection guidance, truthful media-loading state, concurrent media/list reads and direct photo-insight preference review align the corrected iOS behavior.
- Existing optional AI disclosure/consent, Marin voice, trial restriction, hosted sign-in, trusted installation/session rotation, encrypted local capture and account isolation remain. Partner stays web-only. Native purchase links were not introduced.

## Shared-contract and downstream impact

No backend, database, environment secret, public pricing or eligibility changes. Only existing Doppler Android version code advances from 3 to 4. Server APIs remain backward compatible. No Admin, marketing or Expand Systems code impact. QA/operations impact is regression coverage and release evidence below; no new operational resources. iOS is the authoritative submitted baseline and requires no source change for these Android adaptations.

## Verification

Pending final frozen-source checks, signed artifact verification, Chrome console upload and review readback. Earlier intermediate runs passed all unit tests, lint and 17 Android 16 emulator instrumentation tests. Do not interpret intermediate results as final release evidence.

Google Play live check: existing production version 3 is **In review**, not approved. Managed publishing is on. Highest existing bundle code is 3. No public launch is authorized or performed here.

### Integrated gate results

- 228 unit tests: zero failures, errors or skips. New cases cover account-switched delivery contact, challenge/verification payloads, Letter read-only report state, failed credential adoption, resource-scoped deletion proofs, wrong-code retry and cancellation without mutation.
- 17 Android 16 emulator instrumentation tests: zero failures or skips. Includes actual emulator MediaRecorder/codec/Keystore, interruption/background/focus/privacy/duration recovery, onboarding accessibility, trial actions and relationship-map zoom/list/navigation.
- Debug lint/build passed; diff whitespace check passed. Logs: `/tmp/narratrace-android-parity-frozen-tests.log`; unit and connected-test XML under `app/build/`.
- Security/code quality review: existing HTTPS/redirect rules and protected-store encryption retained; new mutation proofs only attach to explicitly named deletion operations; credentials/codes never persist in UI preferences or release notes. Async contact/report/Letter/media actions retain operation-generation checks. Private drafts stay owner-scoped, incomplete delivery never auto-sends, and report holds retain server enforcement. No new permissions, libraries, remote executable logic, database objects or privileged roles.
- UI automation access to the emulator window is unavailable through the desktop tool. Visual/manual live-account acceptance is not claimed. The approved emulator instrumentation checks and fixture contracts are the evidence for this submission; actual Android hardware/Bluetooth remains outstanding as agreed.
