# Narratrace Android architecture and implementation plan

Status: **Discovery complete; awaiting architecture review before implementation**

This plan covers Narratrace user functionality only. Narratrace Admin and Operations are excluded.

## 1. Product parity baseline

Android will be a native peer of the existing SwiftUI application, not a web wrapper and not a reduced companion. The source-of-truth behavior is the combination of:

- the versioned `/api/v1` contracts in `narratrace-app`;
- the native flows and security boundaries in `narratrace-mobile-ios`;
- shared server-side authorization, validation, delivery, storage, and error behavior;
- customer-facing web behavior where the native client intentionally hands off to the authenticated website.

The Android client must support:

- introduction and onboarding;
- invitation-bound Google authentication, authenticator MFA, refresh, inactivity reauthentication, session listing, and session revocation;
- home, storage-capacity visibility, attention/activity state, and pending local work;
- text, photo, audio, and video capture;
- People and relationships;
- guided interviews, text/audio/video responses, legal acceptance, narrative, insights, source traceability, and sharing;
- Memories, privacy state, family sharing, review state, and pinning;
- protected media library, captions, tags, processing, retry, playback, and deletion;
- Letters and Future Messages, including offline drafts and protected audio;
- immediate or future delivery to self or another permitted recipient through the common artifact-delivery contract;
- family membership and invitations;
- Circles, invitations, membership, shared Memories, and shared Letters;
- archive search across Memories, media, People, interviews, and Letters;
- notification consent and preferences;
- profile, appearance, plan/capability visibility, privacy/permissions, security/devices, feedback, and support;
- authenticated web handoffs for full-account export, account closure, billing management, and other deliberately web-only operations.

## 2. Native technology decisions

- **Language:** Kotlin.
- **UI:** Jetpack Compose with Material 3 used as an accessibility foundation, customized to Narratrace tokens and interaction patterns.
- **Architecture:** unidirectional state flow using screen-level ViewModels, immutable UI state, explicit user intents, repositories, and use cases where domain coordination is non-trivial.
- **Concurrency:** Kotlin coroutines, structured concurrency, `StateFlow`, and `SharedFlow` for one-time effects.
- **Networking:** OkHttp plus Retrofit or a small typed adapter over OkHttp. Every response must validate the versioned response envelope and preserve request/support IDs.
- **Serialization:** Kotlinx Serialization with unknown-field tolerance and explicit enums/fallbacks where the API may evolve.
- **Dependency injection:** Hilt, scoped by application, authenticated account, and screen.
- **Navigation:** Navigation Compose with typed routes and verified Android App Links.
- **Local structured state:** Room only for non-secret indexes, retry metadata, and encrypted payload references. Protected content remains encrypted and account-namespaced.
- **Secrets and sessions:** Android Keystore-backed keys; session material encrypted at rest, non-exportable where supported, invalidated on lock-screen/security changes as appropriate.
- **Background work:** WorkManager with explicit network/battery/storage constraints and unique, idempotent work names.
- **Media:** file-backed ContentResolver/MediaStore access, streaming OkHttp request bodies, ExoPlayer/Media3 playback, and no full-video in-memory buffers.
- **Camera/audio:** CameraX and MediaRecorder/AudioRecord behind permission-aware abstractions.
- **Images:** Coil with authenticated short-lived loading and thumbnail-only disk caching.
- **Testing:** JUnit, coroutine test, MockWebServer, Room tests, Compose UI tests, accessibility checks, and physical-device instrumentation.

## 3. Proposed project shape

Use a single Android application module initially, with strongly enforced package boundaries. Split modules only when build performance or ownership justifies the added structure.

```text
app/
  src/main/java/io/narratrace/android/
    app/                 application, root state, navigation, deep links
    core/
      accessibility/     semantics, announcements, focus, reduced motion
      auth/              admission, session rotation, inactivity gate
      crypto/            Keystore, envelope encryption, integrity checks
      database/          Room indexes and migration policy
      delivery/          shared delivery request model and validation mirror
      errors/            versioned API failures and support references
      media/             protected files, streaming, cleanup acknowledgements
      network/           API envelope, clients, interceptors, redaction
      notifications/     channels, consent, privacy-safe rendering
      storage/           capacity, pressure monitoring, purgeable cache
      ui/                tokens and reusable Narratrace Compose components
    feature/
      onboarding/
      home/
      capture/
      library/
      media/
      people/
      interviews/
      memories/
      letters/
      delivery/
      family/
      circles/
      search/
      activity/
      settings/
      account/
```

No Android-specific database, schema, backend service, or duplicated policy engine is proposed.

## 4. Shared API compatibility

Android must consume the same `/api/v1` routes as iOS. Platform differences belong in request metadata or native adapters, never divergent business rules.

Mandatory API behavior:

- require the `X-Narratrace-Api-Version` response contract;
- deserialize the `{ data, meta }` and `{ error, meta }` envelopes;
- surface the support ID without exposing private diagnostics;
- refresh once after an eligible authentication failure, then fail closed;
- never retry mutations without an idempotency strategy;
- preserve server authorization as authoritative;
- treat unknown authorization, ownership, integrity, or delivery state as unavailable;
- keep mobile request and response regression fixtures shared across iOS and Android tests;
- add Android platform acceptance to existing mobile admission and installation contracts without weakening iOS behavior.

Before implementation, generate a checked-in contract inventory from the current routes and compare it with the iOS `AuthenticationAPI` surface. This inventory is documentation/test input, not a new backend schema.

## 5. Authentication and security architecture

1. Fetch a one-time server challenge.
2. Perform Google OAuth authorization code flow with PKCE, state, and server nonce.
3. Submit the invitation, identity assertion, authenticator MFA code, device metadata, and challenge through `/api/v1/auth/native`.
4. Accept only server-issued account identity and access/refresh tokens.
5. Encrypt credentials with an Android Keystore key and store no OAuth client secret.
6. Rotate refresh tokens; reject replay, missing verified email, invitation mismatch, and unsupported identity methods.
7. Reauthenticate after 30 minutes of inactivity and for security-sensitive actions.
8. Revoke local credentials and protected indexes immediately on sign-out, session revocation, account change, or authorization loss.

Additional requirements:

- prohibit screenshots on screens displaying highly sensitive protected content where product review confirms the tradeoff;
- redact tokens, signed URLs, email addresses, filenames, content, and recipient data from logs and crash reports;
- use Android Network Security Configuration to forbid cleartext traffic;
- require HTTPS API origins and reject production endpoint override in release builds;
- verify App Links and reject unverified deep links for invitation or delivery access;
- keep permission prompts behind authenticated, explained user intent;
- make clipboard use explicit and time-bounded for sensitive values;
- include dependency and manifest review in every release gate.

## 6. Protected local storage and media lifecycle

- Namespace encryption keys, protected files, indexes, drafts, and queues by stable server account ID.
- Encrypt artifact drafts with authenticated encryption and verify integrity before decode.
- Store protected originals only in app-private storage.
- Retain a local original until the backend confirms both durable preservation and integrity validation.
- Treat upload completion, HTTP success, or processing start as insufficient for deletion.
- After acknowledgement, delete the local original and verify removal.
- Permit only purgeable thumbnail caches; never cache full audio, video, Letter bodies, or account archives for convenience.
- Use file descriptors/streams for video and large audio. Do not materialize full files as byte arrays.
- Preserve interrupted uploads and edits through an encrypted, idempotent WorkManager queue.
- Evict shared cached content when family or Circle access is revoked.
- Expose device-local pending bytes/items separately from server plan capacity.

## 7. Storage-capacity visibility

The home and capture surfaces must display the server-provided storage summary used by iOS:

- used bytes and label;
- total allowance;
- available allowance;
- percentage used;
- shared inclusion of photos, video, audio, and interview recordings;
- warning treatment at 75% and critical treatment at 90%;
- accessible text equivalents, never color-only status.

Before capture, show recording-specific capacity where the interview API provides it. Under device storage pressure, block unsafe capture early, preserve existing drafts, and provide recovery guidance without claiming server capacity has changed.

## 8. Common delivery rules

All deliverable artifact types must use one Android delivery model mapped to the shared server domain logic:

- `Send now` or `Deliver later`;
- future dates and times only for scheduled delivery;
- creator/self or another permitted recipient;
- self-delivery remains valid when recipient email equals creator email;
- scheduled artifacts remain sealed and inaccessible until delivery time;
- the server owns recipient verification, authorization, revocation, audit, retry, and failure notification;
- local validation improves usability but never replaces server validation;
- date handling uses an instant plus explicit time-zone presentation and is tested across DST boundaries;
- Letters, audio, and video use the same delivery center and status vocabulary.

## 9. Accessibility and native interaction gates

- TalkBack reading and traversal order for every primary flow.
- Font scaling through 200% and Android accessibility sizes without clipping or hidden actions.
- Minimum 48dp touch targets.
- Keyboard, switch access, and directional focus support.
- Visible focus indication and predictable back navigation.
- System Reduce Motion/animation scale respect; no essential information conveyed only through animation.
- Contrast-compliant light and dark themes, high-contrast resilience, and non-color status cues.
- Semantic headings, field errors, progress announcements, live-region discipline, and descriptive media labels.
- Portrait, landscape, split-screen, foldable posture, small phones, and tablets considered from the first screen.
- Permissions include pre-prompt rationale, denial recovery, and settings handoff.

## 10. Notifications and privacy

- Ask for notification permission only after explaining value and receiving user intent.
- Use privacy-safe generic notification copy with no protected names, family data, artifact titles, delivery dates, or content excerpts.
- Define channels for processing, invitations, Letters/delivery, billing/trial, guidance, weekly prompts, re-engagement, yearbook, and interview anniversaries.
- Respect server notification preferences and unregister push tokens on sign-out or disabled notifications.
- Store push tokens only through the existing encrypted/hash backend contract.
- Open notifications through authenticated, authorization-checked routes; locked or revoked content fails closed.
- Produce a Google Play Data safety inventory from actual code and dependencies before release.

## 11. Feature implementation sequence

### Phase 0 — contract and design lock

- Review and approve this plan.
- Confirm dedicated-repository versus existing-repository ownership.
- Approve repository creation before initialization.
- Confirm Android package-name proposal before Play app creation or package registration.
- Inventory the current iOS screens, API payloads, design tokens, and customer-visible copy.
- Establish Android accessibility, privacy, threat-model, and test acceptance checklists.

### Phase 1 — secure application foundation

- Compose theme and accessible component primitives.
- navigation, safe failures, support references, and web handoffs;
- API envelope, error handling, request IDs, redacted logging, and TLS policy;
- Keystore, encrypted account namespaces, Room migrations, and WorkManager queue;
- Google admission, MFA, session restore/rotation/revocation, invitation App Links, and inactivity gate.

Exit gate: authentication and storage threat-model tests pass; protected capture remains disabled until admission succeeds.

### Phase 2 — read experiences and storage visibility

- home, activity/attention, storage-capacity card;
- Library and protected media listing/detail/playback;
- People/detail, Memories/privacy/source, interviews/detail, Letters/detail;
- global search, processing status, and settings foundations.

Exit gate: Android decodes production-compatible fixtures for every read route and fails closed on unknown ownership or integrity state.

### Phase 3 — capture and resilient preservation

- written Memory;
- photo capture/import;
- standalone audio;
- video capture/import using file-backed streaming;
- encrypted drafts, retry queue, reconciliation, capacity gates, and acknowledgement-based deletion.

Exit gate: interrupted/background/low-storage uploads retain originals; acknowledged originals are deleted and verified absent.

### Phase 4 — interviews and People

- create and manage People;
- guided interview creation, Companion guidance, legal notice;
- text/audio/video responses, message deletion, narrative, insights, source traceability, and sharing;
- recording-capacity behavior and protected playback.

### Phase 5 — Letters, delivery, family, and Circles

- Letter creation, audio, offline draft synchronization, and lifecycle actions;
- shared delivery center for immediate/future and self/external delivery;
- family membership, invitations, roles, removal, and Family Mosaic;
- Circle creation, invitations, membership, sharing, and revocation eviction.

Exit gate: all artifact types use the common server delivery contract and scheduled content remains sealed.

### Phase 6 — account, privacy, notifications, and monetization readiness

- profile, appearance, plan/capabilities, permissions, notification preferences, sessions/devices, feedback/support;
- web-only export, closure, and billing handoffs;
- Google Play Billing architecture review for subscription compliance, without creating products until separately approved;
- Data safety, privacy policy, account deletion URL, content declarations, and reviewer access plan.

### Phase 7 — release hardening

- physical-device matrix, accessibility audit, background transfer, poor network, storage pressure, clock/DST, and revocation tests;
- API compatibility regression suite for both iOS and Android;
- Play Integrity decision only after a privacy/security review; no Firebase dependency by default;
- signed internal build, closed testing, store listing, policy forms, and staged rollout only after separate approvals.

## 12. Test strategy and definition of done

Every feature requires:

- domain/unit tests;
- API fixture and error-envelope tests;
- Compose state/restoration tests;
- accessibility semantics and large-font tests;
- authorization loss and stale-session tests;
- offline/retry/idempotency tests for mutations;
- privacy review of logs, notifications, screenshots, and caches;
- parity assertion against the corresponding web/iOS contract;
- customer-visible copy scan ensuring only `Narratrace` terminology is used.

Release gates additionally require:

- no cleartext traffic or committed secrets;
- no full-video memory buffering;
- no deletion before durable-integrity acknowledgement;
- no past scheduled-delivery acceptance;
- successful self-delivery and permitted external delivery;
- scheduled artifacts unavailable before delivery time;
- revoked family/Circle access evicts content and blocks deep links;
- account export and closure remain authenticated web handoffs;
- Play Data safety declarations match observed application behavior;
- shared API behavior remains compatible with iOS.

## 13. Decisions requiring approval after plan review

1. Create a dedicated Git repository for `narratrace-mobile-android`, or place it in an existing repository.
2. Create the Narratrace application in Google Play Console.
3. Reserve/register the Android application ID/package name.
4. Choose and configure Play App Signing and local/CI signing ownership.
5. Create Google OAuth Android client configuration and associate the signing certificate fingerprints.
6. Add any push provider or Firebase project. The default plan creates neither until required and approved.
7. Create Play subscription products and connect purchase entitlement handling.
8. Create CI, automation, deployment tracks, and release integrations.

No item in this section is authorized merely by approval of the architecture plan; each durable external resource must be approved before creation.
