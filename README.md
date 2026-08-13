# Narratrace Android

Native Kotlin and Jetpack Compose implementation of the Narratrace user experience.

Narratrace Admin and Operations are web-only and are intentionally excluded from this application.

## Security posture

- The application fails closed when identity, integrity, authorization, or API compatibility cannot be verified.
- Cleartext network traffic and Android backup/device-transfer extraction are disabled.
- Protected media is encrypted in app-private staging and remains local until the backend confirms durable storage and integrity.
- Secrets, signing material, and production API configuration must never be committed.

## Local requirements

- JDK 17
- Android SDK 36
- Gradle 8.13

## Current milestone

- Google Credential Manager admission with a fresh server nonce
- optional customer authenticator or recovery-code forwarding
- installation-bound identity and encrypted session restoration
- single-flight access-token rotation and a 30-minute inactivity lock
- authenticated Home, Capture, Library, People, and More navigation shell
- standalone audio capture with encrypted retry staging and verified preservation acknowledgement
- guided interviews with AI notice acceptance, text and audio responses, capacity contracts, and safe completion
- photo and file-backed video capture with encrypted chunk staging and resumable transfer
- protected media Library detail and in-app playback
- interview video responses, coverage, highlights, narratives, revocable public story links, and deletion
- network-constrained background reconciliation across process restarts
- Letters with recipient verification, self-delivery, send-now and timezone-safe future delivery
- shared artifact delivery creation, status center, and revocation
- family setup, role-based membership, invitations, Mosaic boundaries, and Extended Family Circles
- explicit completed-interview sharing and delivered Circle Letters
- protected archive search and authoritative in-app Activity
- manual People creation and editing without implicit content sharing
- media captions and custom tags
- profile, English/Hindi preference, optional notification controls, and installation push revocation
- app-private persistent system/light/dark/Narratrace-preview appearance selection
- encrypted offline Letter drafts with authoritative lease-based reconciliation
- privacy-first onboarding and verified family/Circle invitation-link handoff
- optional Doppler-configured Firebase push registration and token rotation
- secure web handoff for archives, billing, closure, and recovery
- feedback and issue reporting with a validated optional image attachment
- privacy and permissions status without surprise permission prompts
- Activity processing details and safe optional-processing retry
- accessible People relationship map and authenticated Yearbook/resource handoff
- complete plan details and privacy-safe support-reference copying
- timezone-safe delivery validation using an IANA zone and local wall-clock value

Run `./gradlew testDebugUnitTest lintDebug` for the current verification suite.
