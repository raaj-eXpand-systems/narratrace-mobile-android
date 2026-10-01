# September 30 synchronization — Android

Baseline: customer web main eafbc329c57e113487b691057c992650a0f12737. Morning Android privacy parity ea3c943 was already present and was not reimplemented. This pass also reviewed afternoon 32b9760 / 1de3a3f and the subsequent strict/natural/stored-control/composer releases.

## Reconciled behavior

- Additive message_kind/messageKind (snake case takes precedence), nullable control_intent, conversationState and experience on detail and all response DTOs. Missing legacy kind means answer. No native interpretation of control text or mutable product rules.
- Interview options uses Material bottom sheet with default handle, heading, Close and ordered server actions. Help opens independent guidance and existing feedback/support, never an interview write. This matches the established native guidance/support model; it is not conversational web AI chat.
- Paused/ended-for-now displays server break copy and resume. Draft remains intact; focus returns when the self-mode field is composed. Together mode is preserved. Controls stop old question speech. Only the latest answer-kind assistant question gets the topic shortcut while active and idle. Labels are ordinary text. Answer-only response actions and completion eligibility exclude help/control exchanges.
- Explicit controls send only control plus a retained Idempotency-Key. Failure preserves the action/key and exposes Retry interview action, disabling other control/answer/media submissions until acknowledged. Schema 503 remains the safe existing error envelope; no automatic text/control retry. Video reconciliation disables concurrent submissions. Media queues retain their existing durable identity and preservation rules.
- Interview chapter permission is separate from account photo permission. Account inventory consumes story references and opens chapter review. Role-blocked owners retain server-authorized revoke, get named-owner guidance, and each view reuses a UUID header for content-free server friction deduplication. Both surfaces refresh uncertain permission results before further writes.
- Uses Keepsake book (PDF) wording. Download/generation remain the previously approved web handoff; no printing/shipping claims or native purchase/upgrade directions were added.

## Full-day impact audit

| Area | Android outcome |
| --- | --- |
| Public-link inventory, revoke, blocked-family permissions, omission requests | Morning implementation retained; existing regression suite rerun |
| Consent-filtered generation and photos | Shared server enforcement; new account permission presentation consumes shared endpoint |
| Legal editorial/material distinction | Existing native gate consumes authoritative booleans and does not compare document dates. Material revisions still block through server status. No versioned editorial-notice metadata exists; web editorial banner has no native equivalent in this baseline |
| Recipient delivery email/download privacy | Server-owned redemption/email payloads; no local recipient-policy implementation to change |
| Marketing OTP/provider cleanup | No Android credentials, marketing write path, or endpoint dependency; no native change |
| Web capture SVG/CSS cascade fix | No Android DOM/CSS; Material theme/native controls retained |
| Answer-only narrative, coverage, search, keepsake content and allowances | Existing shared API outputs/authorization remain authoritative; no local duplicate policy |

## Security and Code Quality review

No new dependencies, credentials, storage, authorization or database resources. Requests retain authenticated versioned API transport, no-store behavior and server role/ownership validation. Control payloads are allowlisted and omit drafts. Server presentation metadata cannot open arbitrary URLs. Consent header contains only a random view identifier, never content/identity. The feature neither bypasses trial/store restrictions nor weakens token handling. Review covered duplicate submissions, uncertain results, help separation, role-blocked revocation, unknown/legacy message fields, and removal of obsolete chapter/photo UI branches.

Validation: `./gradlew test lint assembleDebug`; 238 debug and 238 release unit tests. New regressions cover legacy/alias kinds, stored state despite changed text, replay metadata, explicit control payload, 503 manual retry retaining identity without hidden network retries, role-blocked revoke, and account permission endpoints. Existing source assertion was updated to chapter/account-photo controls after removing the obsolete loop. `git diff --check` passed.

Runtime limits: no attached/running Android device or emulator was available at inspection; the existing medium_phone AVD was listed but not started. New sheet, screen reader, keyboard/focus and theme layouts are source-reviewed and compiled, not claimed as executed visual/device acceptance. No production data mutation, migration, push, Play upload or release occurred. Cross-platform and QA/OPS closure belongs to the primary synchronization review.
