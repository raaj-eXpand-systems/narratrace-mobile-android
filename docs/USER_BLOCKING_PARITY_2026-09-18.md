# User blocking parity — 18 September 2026

## Scope and baseline

Owner authorized iOS, Android, Admin, and QA synchronization after production verification. Final authoritative customer-web baseline: `d8e6a93e624415abb87556a7ffa47e9289dd8f7b`, verified Ready on production from current main by the primary agent. Circle selectors and current-member identity are included in that baseline. No Play publication is authorized by this implementation record.

## Android implementation

- Authenticated versioned GET/POST/DELETE `/api/v1/account/blocks`; normal installation-bound token recovery and existing legal/lifecycle denial handling remain intact.
- Block from a visible active or pending Family member, matching iOS and the shared API. Circle blocks use a visible member ID or the explicit Circle owner selector, without querying or exposing private email addresses. The server-provided current-member marker suppresses self-block controls; absent markers from older servers decode as false and self-block remains server-denied.
- Profile and preferences → Blocked people lists only outgoing block account references and creation dates. Unblock has a confirmation; no account-email lookup exists.
- Confirmation states owner removes target, other shared group membership is left, pairwise deliveries are revoked, departing shared Memories become private, and unblocking restores nothing.
- Successful actions display a native confirmation after refresh. Successful and repeated successful mutations invalidate the entire authenticated Compose tree. Old shared content and in-flight view scopes are discarded; repositories hold no remote content cache and HTTP protected content is no-store. Encrypted owned captures and offline letter drafts are preserved.
- Delivery center currently contains outgoing deliveries only. There is no native received-artifact screen or valid recipient delivery reference to attach a block action to. Server enforcement still protects those deliveries.

## Security gate

Uses existing HTTPS-only no-redirect transport, bearer authentication and request identity. No credentials/contact directory endpoints/logging/new dependencies introduced. Server validates visible source authorization, group governance, both-direction restrictions and mutation rate limits; native code cannot grant access or alter those rules. UI invokes blocking only through contextual sources. API errors remain visible; failed mutations are not displayed as successes. Repeated successful mutations still clear remote view state. Owned local drafts are never passed to erasure logic.

## Code quality evidence

Final verification: 186 unit tests pass (five focused blocking tests), debug build and Android lint pass with zero errors. Added tests cover POST/DELETE JSON and bearer/no-store headers, additive outgoing list decoding without email fields, and invalidation on idempotent/mismatched successes but not failures. Unsigned release build also passes, including R8 and lintVital; APK contains no Markdown or SQL entries. Final Circle integration is included in the passing suite; added selector serialization and backward-compatible current-member tests. Final command: `JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home ./gradlew testDebugUnitTest assembleDebug lintDebug assembleRelease`. Evidence: `/tmp/narratrace-android-blocking-family-reconciliation.log`. `git diff --check` passes. Native code remains uncommitted for primary-agent reconciliation; no build was uploaded.

No persistent resource, database migration, production backend edit, store upload, or new dependency created. Theme colors and native Material dialogs/buttons follow active appearance; heading semantics, scrollable list, explicit confirmation and progress states retained. Physical-device TalkBack, large-text and authenticated multi-account interaction remain manual checks; automated build/lint is not evidence of those runtime checks.
