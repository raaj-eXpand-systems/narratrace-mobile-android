# Narratrace `/api/v1` contract inventory

**Purpose:** this is the build spec for the Android client. Android consumes the same
`/api/v1` contract as iOS. Where this document and the Kotlin disagree, this document —
and ultimately the server source — wins.

**Generated:** 2026-09-01, synchronized through `narratrace-app@ccca8b6`.
**Status:** Phase 0c deliverable. Regenerate before each phase; the server moves.

Sections marked **⚠ UNVERIFIED** were inferred and must be confirmed against the SQL
migrations or a live response before Android models them.

---

## 1. Shared infrastructure

### Envelope — `lib/apiContract.ts`

```ts
export const API_VERSION = '1' as const
sendApiSuccess<T>(res, requestId, data, status = 200)
sendVersionedApiError(res, requestId, status, code, message, field?)
```

**Success:**
```json
{ "data": <T>, "meta": { "apiVersion": "1", "requestId": "…", "supportId": "…" } }
```

**Error:**
```json
{ "error": { "code": "…", "message": "…", "field": "…" },
  "meta": { "apiVersion": "1", "requestId": "…", "supportId": "…" } }
```

`error.field` is **omitted entirely** when absent — not `null`. Kotlin: nullable with a
default, not a required field.

`meta.supportId` is always identical to `meta.requestId`. They are not distinct values
despite the naming. Do not build UI implying they differ.

**Error codes (closed union `ApiErrorCode`):** `AUTHENTICATION_REQUIRED`,
`AUTHENTICATION_METHOD_UNSUPPORTED`, `FORBIDDEN`, `INVALID_REQUEST`, `VALIDATION_FAILED`,
`IDEMPOTENCY_CONFLICT`, `PROMOTION_NOT_AVAILABLE`, `METHOD_NOT_ALLOWED`, `RATE_LIMITED`,
`RESOURCE_NOT_FOUND`, `SERVICE_UNAVAILABLE`, `INTERNAL_ERROR`.

Android must deserialize unknown codes to a fallback rather than throwing — the union will grow.

### Response headers

| Header | Value |
|---|---|
| `X-Request-Id` | the request id |
| `X-Support-Id` | same value as `X-Request-Id` |
| `X-Narratrace-Api-Version` | literal `"1"` |

> **Correction to `ANDROID_ARCHITECTURE_PLAN.md` §4.** The plan says to "require the
> `X-Narratrace-Api-Version` response contract." That header is **response-only**. No server
> code reads it from a request; there is no version negotiation and nothing rejects a missing
> or wrong request-side version header. Android may *assert* on the response header, but must
> not expect the server to enforce anything if Android sends one.
>
> Same for `X-Narratrace-Platform` and `X-Narratrace-App-Version` — iOS sends both; **nothing
> on the server consumes them.** Platform and app version reach the server only through the
> `auth/native` and `mobile/installation` request bodies. Send them for parity, but don't
> build behavior on them.

### Request ID — `lib/apiErrors.ts`

`getRequestId(req)` honours an inbound `X-Request-Id` **only** if it matches
`^[a-zA-Z0-9_-]{8,80}$`; otherwise it generates a UUID. Android should send a fresh
lowercase UUID per request (iOS does) and surface the returned `X-Support-Id` in error UI.

`lib/apiErrors.ts` also exports a **legacy non-v1** `sendApiError` emitting
`{ error: string, referenceId: string }`. No `/api/v1` route uses it. Ignore it.

### Identity resolution

| Helper | Accepts | Used by |
|---|---|---|
| `lib/mobileRequestIdentity.ts` | **bearer only** (web resolvers stubbed to `null`) | most `/api/v1` routes |
| `lib/apiIdentity.ts` | web session **or** bearer | `/auth/sessions`, `/me` |
| `lib/customerRequestIdentity.ts` | web session **or** bearer | `/billing/*` only |
| `lib/mobileAccessIdentity.ts` | hash lookup of an access token | inner layer |

**Fail-closed rule (`apiIdentity.ts`):** if an `Authorization: Bearer …` header is present it
is the *only* credential considered — never falls back to a cookie. Failure reasons:
`missing` | `invalid_bearer` | `unsupported_bearer`.

`ApiIdentity` = `{ accountId, providerSubject, email, authenticationMethod:
'web_session'|'mobile_access_token', mobileSessionId?, mobileInstallationId? }`.

`resolveMobileAccessIdentity` rejects unless **all** hold: token parses as kind `access`;
token row not revoked and not expired; session row exists with matching `account_id`, not
revoked, has an `installation_id`; both `expires_at` and `family_expires_at` are in the
future; identity row matches the account; `accounts.status === 'active'`.

### Tokens — `lib/mobileTokens.ts`, `lib/mobileSession.ts`

Opaque, not JWTs. Access prefix `ntm_at_`, refresh prefix `ntm_rt_`, 32 random bytes
base64url (43 chars). Pattern `^ntm_(at|rt)_([A-Za-z0-9_-]{43})$`; anything over 64 chars is
rejected outright. **Only SHA-256 hex hashes are stored server-side.**

`ACCESS_TTL_MS` = **15 minutes**. `REFRESH_TTL_MS` = **30 days**.

### Pagination — `lib/apiPagination.ts`

`DEFAULT_PAGE_SIZE = 25`, `MAX_PAGE_SIZE = 100`. `limit` must be a 1–3 digit string in
`[1,100]`. Cursor is an **HMAC-signed opaque string**
`"<base64url(JSON{createdAt,id})>.<base64url-hmac-sha256>"`, max 500 chars.

Android must treat the cursor as an opaque `String` and echo it verbatim. Never parse it.

### Idempotency — `lib/apiIdempotency.ts`

Header `Idempotency-Key`, pattern `^[A-Za-z0-9._:-]{16,180}$`. The server hashes a canonical
stable-JSON form of the request; a reused key with a *different* body hash returns
`409 IDEMPOTENCY_CONFLICT` — though some routes return `409 INVALID_REQUEST` instead.
Android must handle both.

Replays return `200` with `replayed: true` rather than `201`.

---

## 2. Authentication flow

### Hosted protocol v1 — authoritative path for capable Android clients

`GET /mobile/runtime-config?platform=android` advertises `authentication.mode=hosted`,
protocol version `1`, and `/api/v1/auth/hosted/start`. The client creates a 43-character
S256 PKCE verifier, stores it only in app-private AES-GCM encrypted pending state, and
submits `platform`, installation UUID, semantic app version, bounded OS version, and
`codeChallenge` to `POST /auth/hosted/start`.

The returned `authorizeUrl` must be an HTTPS `www.narratrace.io/auth/hosted/{opaque}` URL
with no query or fragment. It opens in an Android Custom Tab, never a WebView. The only
accepted return is the verified App Link
`https://www.narratrace.io/mobile/auth/callback/android?code=…&state=…`. The client
requires exact retained transaction state and rejects alternate hosts, schemes, paths,
fragments, duplicate parameters, and extra callback data.

`POST /auth/hosted/exchange` submits the retained transaction, single-use code, PKCE
verifier, and identical installation metadata. The client fetches `GET /bootstrap` with
the exchanged access credential and atomically protects the rotating token pair only
after that authoritative projection validates. Bootstrap provides one server-authored `hosted_web` upgrade destination;
`returnsToApp` must be `false` for plan upgrades, plan changes, add-ons, and billing
recovery. Android contains no native purchase logic and constructs no upgrade URL.
Same-plan repurchase prevention and valid-upgrade eligibility are therefore enforced by
the hosted server workflow, not duplicated in Android. The client must not infer an
eligible target from the current plan or offer a native checkout fallback when the hosted
workflow declines a change.

The current hosted workflow distinguishes repeat purchases without adding a native billing
contract. A second A Life purchase is rejected with
`ADDITIONAL_STORYTELLER_RECOMMENDED` and directs the authenticated web experience to the
one-time $79 Additional storyteller add-on. A second Family purchase is rejected with
`FAMILY_ALREADY_ACTIVE` because Family already includes unlimited storytellers. Tier changes
use `PLAN_CHANGE_REQUIRED`. Android must present only its existing server-authored Account
handoff; it must not reproduce these prices, limits, redirect URLs, or conflict decisions.

Production checkout validates the requested immutable SKU against its own Stripe lookup
key. It does not require unrelated catalog entries to be available before that purchase can
continue. Web checkout still owns same-plan denial, upgrade eligibility, and entitlement
reconciliation. This is not an Android catalog contract: Android sends no SKU or price ID
and must not cache or reconstruct either one.

The web checkout's self-keepsake choice is explicitly worded as an archive for the buyer,
with the plan and sign-in remaining in that buyer's account. Android does not render the
keepsake-card purchase form or infer recipient access from artifact-delivery records, so no
native copy or delivery-payload change is introduced by that clarification.

The customer-web root-route recovery for a stale `mfa_required` browser session is also
web-only. It revokes browser trust and clears the web session before returning to sign-in;
it does not change the hosted mobile protocol or installation-bound token contract.
Android already fails safely at its corresponding boundary: an unauthorized refresh is
terminal, destroys the locally protected session, and returns the app to native sign-in.
Transient transport and server failures retain the protected session for a later retry.

The following challenge/native routes remain only for a bounded older-server rollback
window. They are selected solely when runtime config predates the hosted contract and
must not receive new onboarding policy.

### `POST /auth/challenge` — unauthenticated
No body. Rate limit **10 / 60 s per IP** → `429 RATE_LIMITED`.
Data: `{ nonce, expiresAt }` — 32 random bytes base64url, ISO expiry at now + 5 min.
Server stores only `sha256(nonce)`. Storage failure → `503` carrying code `INTERNAL_ERROR`
(status/code mismatch is deliberate in source).

### `POST /auth/native`

Body (`AdmissionInput`, `lib/mobileAdmission.ts`):

| Field | Notes |
|---|---|
| `provider` | `'google'` \| `'apple'` |
| `authorizationCode`, `codeVerifier`, `redirectURI` | Google PKCE path |
| `idToken` | Apple path |
| `nonce` | the challenge nonce, plaintext |
| `inviteCode?` / `inviteHandoff?` | mutually exclusive — supplying both fails |
| `installationId` | lowercase UUID |
| `platform` | `'ios'` \| `'android'` — **`'android'` is already accepted** |
| `appVersion` | `^\d+\.\d+\.\d+([+-]…)?$` |
| `osVersion?` | ≤ 40 chars |
| `mfaCode` | required only when the customer enabled authenticator protection |

**Every rejection collapses to `401 AUTHENTICATION_REQUIRED`, message "The sign-in could not
be completed."** No cause discrimination. Android cannot show a specific error and must not
try to infer one. Causes: bad provider; non-UUID `installationId`; `appVersion` fails semver;
`osVersion` > 40; both invite fields; Google code exchange or Apple assertion fails; no
verified email; email not an invitable identity (`isInvitableIdentityEmail` — Gmail/Apple
only); missing or invalid `mfaCode`; RPC `admit_mobile_identity` returns ≠ `'admitted'`.

**MFA follows the customer account setting on native admission.** When enabled, TOTP is consumed via `consume_mfa_totp_step`;
recovery codes accepted and atomically removed.

Narratrace role holders are stricter: authenticator enrollment is mandatory and every new
hosted or compatibility admission requires a fresh authenticator or recovery-code result.
A trusted-browser cookie is never sufficient for a role-holder sign-in. The hosted page
enforces this before exchanging the Android transaction; compatibility admission enforces
the same rule through `mfa_enrollment_required` and `mfa_required`. Ordinary unenrolled
customers remain quiet and are not prompted for an email OTP or optional authenticator.
The retired protected-asset email-OTP continuation fields and response state are absent
from the native request, response decoder, coordinator, and compatibility sign-in UI.

Apple hashes the nonce (`sha256` hex) before assertion verification; Google passes it raw.

Rate limit 12 / 900 s per IP. Data: `{ accessToken, refreshToken, accessExpiresAt }`.

### `POST /auth/refresh`
Body `{ refreshToken }`. No bearer. RL 600/60 s per IP, then 20/60 s per token hash.
Rotation is atomic (`rotate_mobile_session`) — new access **and** new refresh, returned only
when the RPC yields `status === 'rotated'`. Reuse of a consumed refresh fails.
Data: `{ ok: true, accessToken, refreshToken, accessExpiresAt }` — the stray `ok` field is
real; the route spreads the whole RPC result. Ignore it. Any failure → `401`.

### `GET|DELETE /auth/sessions` — bearer **or** web
`GET` → `{ sessions: [{ id, platform, appVersion, osVersion|null, lastActiveAt,
authenticatedAt, expiresAt, isCurrent }] }`. Only non-revoked installations with a live,
non-replaced session; sorted current-first then `lastActiveAt` desc.
`DELETE` body `{ scope: 'current'|'all' }`. `'current'` requires a mobile session — a web
caller gets `400 VALIDATION_FAILED` on field `scope`. → `{ revoked: true, scope }`.

### `POST /auth/link` — bearer with `mobileSessionId`
Body `{ provider, idToken, nonce }`. RL 10/3600 s per account. → `{ linked: true }`.
**iOS never calls this.** Android: skip unless product asks for it.

---

## 3. Route inventory

**Auth legend:** `bearer` = mobile access token only; `bearer|web` = either; `none` =
unauthenticated. **No route enforces MFA at request time** — MFA is admission-only.
"family perm X" = `authorizeFamilyPermission(email, X)`.

All request/response bodies are camelCase JSON in the standard envelope **except where
flagged**. Two exceptions exist and are called out inline.

### Identity / account

| Route | Methods | Auth | Request | Data |
|---|---|---|---|---|
| `/me` | GET | bearer\|web | — | `accountId, providerSubject, email, authenticationMethod` |
| `/account` | GET | bearer | — | `status, plan, billingCycle, activated, trialLifecycleStage, trialEndsAt, currentPeriodEndsAt, daysRemaining, hasAccess, canReadArchive, storage{usedBytes,availableBytes,totalBytes,usedLabel,availableLabel,totalLabel,usedPercent}, capabilities{captureMemories,createLetters,managePeople,familyCircles}, experiment{cardGateArm:'A'|'B',experienceFirst,resourceState:'available'|'claimed'|'completed'|null}` |
| `/account/lifecycle` | GET | active or installation-bound lifecycle bearer | — | `state,effectiveAt,recoveryEndsAt,purgeEligibleAt,reasonCode,appealStatus,appealUrl,localDataDisposition,installationBound` |
| `/account/closure` | GET, POST | active or lifecycle bearer | POST `{action:'close'|'reopen'}`; close requires authentication within 10 minutes | GET `accountClosed,closedAt,graceEndsAt,daysLeft,expired,supportRef,purgeScheduledAt,closureFinalizedAt,refundAmount,currency,requiresRecentAuthentication`; POST close `ok,accountClosed,supportRef`; reopen `ok,accountClosed,restoredStatus,requiresSignIn` |
| `/profile` | GET, PATCH | bearer | PATCH any of: `displayName` ≤80, `birthYear` int 1900…year−5 or `null`/`""`, `preferredLanguage` `'en'\|'hi'` | `profile{email, displayName, birthYear, preferredLanguage}` |
| `/legal/acceptance` | GET, POST | bearer | POST: `acceptTerms`, `acknowledgePrivacy`, `acknowledgeAiNotice` — **all three literal `true`** | `termsAccepted, aiNoticeAcknowledged, termsVersion, privacyVersion, aiNoticeVersion, acceptedAt` |
| `/feedback` | POST | bearer | `senderName?`, `kind` (`'issue'`, else coerced `'feedback'`), `message`, `pageReference` (required when issue), `screenshot?{name,type,data}` base64 PNG/JPEG/WebP ≤5 MB. 8 MB body cap. RL 10/hr | `submitted: true` |
| `/home` | GET | bearer | — | `recentMemories[]` (≤3), `attention[]` (≤3), `hasMoreActivity` |
| `/search` | GET | bearer | `q` trimmed ≤100; <2 chars → empty. RL 30/60 s | `query`, `results[{id:"kind:uuid", resourceId, kind: memory\|person\|interview\|letter\|video\|photo, title, subtitle}]` |

`/account.storage` is the source for the capacity card required by plan §7 (75% warning,
90% critical). Note the server already supplies pre-formatted `*Label` strings — use them
rather than formatting bytes on-device, so iOS and Android read identically.

`/account.experiment` retains its historical field name for backward-compatible clients,
but it is no longer a random product assignment. Arm `B` means the verified account owns a
saved `guided_interview` onboarding choice; Arm `A` means `plan_purchase`. The server may
reconcile an unused historical assignment to that saved choice. Browser cookies and URLs
are attribution hints only and are never routing authority after sign-in.

The guided choice can grant one interview before plan activation. It does not grant photo,
standalone audio/video, or written-memory capture while `hasAccess` is false. Native clients
therefore enable only the guided interview when `experienceFirst` is active and the resource
is not complete. A paying account (`hasAccess == true`) always uses its full entitlements,
even if its original guided choice remains in the response. Archive-only accounts keep
read access through `canReadArchive` without gaining capture access.

An untouched, unpaid account may change its saved choice to `guided_interview` through the
existing authenticated web workflow. No new Android request or response field is required:
the next authoritative `/bootstrap` and `/account` projections expose the result through the
existing `experiment.experienceFirst` and `resourceState` fields. Android must remain a
projection consumer and must not reproduce the server's untouched-account eligibility check.

#### Gift and checkout boundary (2026-09-01)

Gift purchase, recipient correction, welcome-code rotation, printable-card access, and Cart
state remain authenticated hosted-web workflows. Android does not call the web-only
`/api/stripe/production-checkout` or `/api/account/production-gifts` routes and must not
reimplement their authorization or validation. The hosted checkout contract now supports
all four production products (`a_life_essential`, `a_life_complete`, `family_essential`,
`family_complete`) and requires a recipient name, relationship (1–40 characters), a
recipient email distinct from the purchaser for recipient-owned gifts, and a server-resolved
delivery date plus `morning`, `afternoon`, or `evening` in an IANA timezone.

The welcome code, not a recipient-email match or a native deep link, authorizes initial
recipient redemption. Correcting an unredeemed recipient rotates the welcome code and
invalidates the old code. After hosted redemption, Android consumes only the existing
server-authoritative `/bootstrap` and `/account` projections. Family gifts therefore arrive
as the existing `productFamily:'family'`, `productTier`, annual period, access, capability,
and canonical subscription-state fields. These changes are additive and require no native
gift, Cart, payment, address, or subscription-lifecycle state.

Before hosted redemption, the recipient chooses English (`en`) or Hindi (`hi`). The web
flow persists that choice through the existing profile preference before redeeming the
welcome code. Android already decodes `preferredLanguage` from `/bootstrap` and `/profile`,
validates the same two values for later profile changes, and does not need a gift-specific
language DTO or redemption request.

The current legal gate returns Terms version `2026-09-01.2` and Privacy version `2026-09-01.1`. Android continues
to trust the returned acceptance booleans and versions rather than bundling a native legal
version. Its customer-visible legal-change summary describes Stripe-hosted collection of
payment credentials and checkout addresses; Android never receives or stores that data.

### Mobile platform

| Route | Methods | Auth | Request | Data |
|---|---|---|---|---|
| `/mobile/runtime-config?platform=android` | GET | public, rate-limited | semantic platform query | `minimumSupportedVersion,maintenance,cacheForSeconds,behavior{capture:'last_known_good_then_allow_offline',upload:'fail_closed',billing:'fail_closed',quota:'fail_closed'}` |
| `/mobile/installation` | PATCH | bearer + installation | `appVersion` semver **required**, `osVersion?` ≤40, `pushToken?` `^[a-f0-9]{64,512}$`, `notificationsEnabled?` (`false` clears stored token) | `updated: true` |
| `/mobile/notification-preferences` | GET, PATCH | bearer | PATCH: subset of **snake_case** keys `processing_ready, invitations, letters, trial_and_billing, product_guidance, weekly_memory_nudge, re_engagement, yearbook_reminder, interview_anniversary`, all boolean. Empty/invalid → 400 | `preferences{…same snake_case keys…}` |
| `/mobile/activity` | GET | bearer | `limit` 1–100 default 25, `cursor` opaque | `items[]`, `nextCursor` |
| `/mobile/offline-lease` | POST | bearer + installation | — RL 20/hr | `lease{leaseId, expiresAt (+12 h), scopes:['recent_content.read'], authoritative:false}` |
| `/mobile/offline-letter-drafts` | POST | bearer + installation | **`Idempotency-Key` required.** `clientDraftId`, `baseRevision` (non-neg int), `toName?` ≤200, `subject` 1–200, `body` ≤10000, `unlockAt?` ISO. RL 120/hr | `draft` — **⚠ UNVERIFIED**, see below |

> **⚠ `/mobile/notification-preferences` is the only snake_case route in the entire API**,
> for both request and response. Everything else is camelCase. If Android applies a global
> `JsonNamingStrategy`, this route breaks. Model it with explicit `@SerialName`.

`/mobile/activity` items are a **discriminated union on `kind`**:
- `{id, kind:'notification', createdAt, title:'Narratrace', body, route}`
- `{id, kind:'processing', createdAt, jobType, resourceType, resourceId, state:'preserved'|'needs_attention'|'processing', progress, failureCode, announcement}`

Note `title` is the literal constant `'Narratrace'` — privacy-safe by design (plan §10).

> **⚠ UNVERIFIED — `offline-letter-drafts.draft`.** This is the untyped return of the Postgres
> RPC `sync_offline_letter_draft`. Only `status` is referenced in TypeScript (branches on
> `'ok'` / `'idempotency_conflict'`). **Read the SQL migration or iOS's `LetterDraftState`
> before modelling this in Kotlin.** Do not guess.

### Memories / media / uploads / processing

| Route | Methods | Auth | Request | Data |
|---|---|---|---|---|
| `/memories` | GET, POST | bearer | POST: `Idempotency-Key`; `format:'written'`, `title` 1–120, `content` 1–50000; family perm `content.create` | GET: `mode:'mosaic'\|'memories'`, `memories[{id, sourceInterviewId, sourceMessageId, title, excerpt, memoryType, visibility:'private'\|'family', status:'active'\|'review_required', pinned, isOwner, updatedAt}]`. POST 201: `kind:'created', id, replayed` |
| `/memories/[id]` | GET, PATCH | bearer | PATCH ≥1 of: `visibility`, `pinned`, `status:'active'\|'dismissed'` | `memory{…}` |
| `/media` | GET | bearer | — | `media[{id, kind:'video'\|'photo'\|'written'\|'audio', title, state, duration, createdAt}]` |
| `/media/[id]` | GET, PATCH, DELETE | bearer (PATCH + `content.update`) | PATCH: `customTags` (≤10, ≤30 chars each) **or** `caption` ≤300 — presence of the `customTags` key selects the branch | GET: `kind:'found', media{…, text, transcript, summary, caption, narrative, tags[], customTags[], insights[{label,value}], playbackUrl, expiresIn, rendition}`. PATCH: `kind:'updated', media{…}` or `kind:'updated', customTags[]`. DELETE: `deleted:true` |
| `/uploads` | POST | bearer | `action:'authorize'\|'confirm'`, `kind:'photo'\|'audio'`, `filename` ≤200 no `/`or`\`, `mimeType`, `fileSize` (photo ≤50 MB, audio ≤25 MB), `fileHash` sha256 hex; `storagePath` for confirm | authorize: `kind:'authorized', uploadUrl, storagePath`. confirm: `kind:'preserved', media{id, mediaKind, createdAt}, preservationAcknowledgement{originalDurablyStored, integrityVerified}` |
| `/videos` | GET, POST | bearer | POST: `filename, fileSize, mimeType, fileHash`. GET: `?id=<cloudflareVideoId>` | POST: `kind:'authorized', uploadUrl, videoId`. GET: `kind:'found', video{id, videoId, state, duration, createdAt, preservationAcknowledgement\|null}` |
| `/processing/[id]` | GET, POST | bearer | POST = retry, no body | GET: `kind:'found', job{id, jobType, resourceType, resourceId, state, progress, failureCategory, canRetry, createdAt, updatedAt}`. POST: `kind:'retried'` |
| `/artifact-deliveries` | GET, POST, DELETE | bearer | POST: `uploadId, recipientName ≤100, selfDelivery, deliveryMode:'now'\|'later', deliverAt?, recipientEmail?`. DELETE `?id=`. RL 30/day | see **defect** below |

**`preservationAcknowledgement{originalDurablyStored, integrityVerified}` is the deletion
gate** required by plan §6. Android deletes a local original **only** when both are `true`.

Customer-web resource deletion now also requires a single-use, purpose-bound
`resource_delete` proof before its browser-session routes delete a resource. That proof is
an HttpOnly web cookie bound to a web MFA session and is not accepted by `/api/v1` bearer
routes. Android must not scrape, mint, persist, or replay it.

The mobile DELETE routes for interviews, individual interview responses, media, Letters,
and Circles instead require their own server-side recent-auth precondition. Android does
not currently expose individual-response deletion, but any future caller inherits this
same contract. The installation session's `authenticated_at` must be no more than 10
minutes old; otherwise the route returns versioned HTTP `428` with
`PRECONDITION_REQUIRED` and `Sign in again before deleting this resource.` Android
classifies that separately from legal acceptance, destroys the old protected credential,
and starts normal admission. It never retries the DELETE automatically; the member must
confirm deletion again after signing in.
HTTP 200, upload completion, and processing start are all insufficient.

#### ⚠ DEFECT — `POST /artifact-deliveries` response shape (server bug, verified)

`lib/artifactDelivery.ts` returns `delivery: inserted.data`, where the insert selects
`'id,state,deliver_at,recipient_email'`. So the POST response `delivery` object is:

```json
{ "id": "…", "state": "…", "deliver_at": "…", "recipient_email": "…" }
```

— **snake_case**, and missing `uploadId`, `artifactKind`, `recipientName`, `selfDelivery`,
`createdAt`. The `GET` handler on the same route explicitly maps to camelCase
(`pages/api/v1/artifact-deliveries/index.ts:31-33`), so **GET and POST return different
shapes from the same resource.**

iOS decodes both into one `RemoteArtifactDelivery` with camelCase keys, no
`keyDecodingStrategy`, and non-optional `artifactKind`, `recipientName`, `selfDelivery`,
`deliverAt`, `recipientEmail`. **That decode cannot succeed on POST.**

Consequence on iOS today: the delivery *is* created server-side and the recipient
verification email *is* sent, but the app surfaces a decoding error. A user who retries
creates a duplicate delivery and the recipient gets a second email.

**Fix the server, then model one type.** Android must not ship a workaround that
accommodates two shapes — that would cement the bug into a second client.

### Interviews

| Route | Methods | Auth | Request | Data |
|---|---|---|---|---|
| `/interviews` | GET, POST | bearer\|web | GET: `limit`, `cursor`; perm `vault.read`. POST: `Idempotency-Key`; `subjectName` 1–200, `subjectRelation?` ≤100, `lifeDecade?` 0–9; perm `content.create`; capability `interviews`; RL 20/hr | GET: `interviews[{id, subjectName, subjectRelation, lifeDecade, status:'active'\|'complete', messageCount, createdAt, updatedAt}], nextCursor`. POST 201 (200 replayed): `interview{…}, replayed` |
| `/interviews/recording-capacity` | GET | bearer + `content.update` | — | `remainingBytes, remainingLabel, audioMaxSeconds, videoMaxSeconds` |
| `/interviews/[id]` | GET, PATCH, DELETE | bearer (PATCH + `content.update`) | PATCH `status:'active'\|'complete'` | GET: `interview{…}, messages[{id, role:'user'\|'assistant', content, hasMedia, mediaType, createdAt}], narrative`. PATCH `updated:true`. DELETE `deleted:true` |
| `/interviews/[id]/responses` | POST | bearer | `Idempotency-Key`; `content` ≤4000. RL 100/day | `message{…}, replayed` |
| `/interviews/[id]/audio-responses` | POST | bearer | **raw audio body, not JSON.** `Content-Type` ∈ `MOBILE_AUDIO_TYPES`; `Content-Length` ≤ `MAX_MOBILE_AUDIO_BYTES`. `Idempotency-Key`. Requires current AI legal acceptance else **`428`**. RL 100/day | `message{…}, replayed` |
| `/interviews/[id]/video-responses` | POST | bearer + `content.update` | `action:'authorize'` → `mimeType` (`video/mp4`\|`video/quicktime`), `fileSize` ≤ `INTERVIEW_VIDEO_MAX_BYTES`. `action:'confirm'` → `Idempotency-Key`, `videoId`, `content?`. AI acceptance required (`428`). authorize RL 20/hr | authorize: `uploadUrl, videoId, maxSeconds`. confirm: `message{…}, replayed` |
| `/interviews/[id]/messages` | DELETE | bearer, owner only | `messageId` | `deletedIds[]` — the user message **plus the immediately preceding assistant message** |
| `/interviews/[id]/messages/[messageId]/audio` | GET | bearer | — | **RAW BYTES, not enveloped.** `Content-Type` = stored mime, `Content-Disposition: inline`. Errors still use the JSON envelope |
| `/interviews/[id]/messages/[messageId]/media` | GET | bearer + `vault.read` | — | `url` (HLS `…/manifest/video.m3u8`), `expiresIn: 3600`. Still processing → **`202` with code `SERVICE_UNAVAILABLE`** |
| `/interviews/[id]/narrative` | GET, POST | bearer (POST + `content.update`, requires `status==='complete'`, RL 3/hr) | — | `narrative` (string\|null) |
| `/interviews/[id]/insights` | GET | bearer + `vault.read` | — | `covered[]`, `highlights[{id, title, excerpt, type}]` |
| `/interviews/[id]/share` | GET, POST, DELETE | bearer (POST/DELETE + `content.update`); requires non-empty narrative | — | `shareToken` (string, `null` after DELETE) |

**`428 PRECONDITION_REQUIRED` on the AI legal gate** is a first-class Android flow, not an
error toast: it must route the user to legal acceptance and resume the capture.

**`202` on media playback means "still processing"** — Android must poll or show a
processing state, not treat 2xx as success-with-payload.

### Letters

| Route | Methods | Auth | Request | Data |
|---|---|---|---|---|
| `/letters` | GET, POST | bearer | POST: `Idempotency-Key`; `recipientName, subject, body, selfDelivery, deliveryMode:'now'\|'later', deliverAt?, recipientEmail?, circleId?, circleMemberEmail?`. Capability `letters.create`; perm `content.create`; **hard cap 20 letters per account** | GET: `letters[{id, recipientName, subject, unlockAt, delivered, recipientVerified, isOwner, createdAt}]`. POST: `kind:'created', id, replayed, verificationPending` |
| `/letters/[id]` | GET, PATCH, DELETE | bearer | PATCH `action:'resend_verification'\|'update_recipient_email'` (+`recipientEmail`) | GET: `letter{id, recipientName, recipientEmail, subject, unlockAt, delivered, recipientVerified, createdAt, hasAudio, isOwner, sharedDeliveryManaged, canCancel, unlocked, body}` — **`body` is `null` until `unlocked`**. PATCH: `kind:'verification_sent', recipientEmail`. DELETE: `kind:'deleted'` |
| `/letters/[id]/audio` | GET, POST | bearer (POST + `content.update`, RL 20/day) | POST: **raw audio body**; `Content-Type` ∈ `audio/mp4, audio/mpeg, audio/wav`; ≤20 MB | POST: `preserved:true`. GET: `url` (signed 300 s), `mimeType`, `expiresIn:300` |

`body === null` until `unlocked` is the server enforcing the sealed-until-delivery rule from
plan §8. Android must never cache a letter body and must render the sealed state from
`unlocked`, not from a local clock comparison against `unlockAt`.

### Family

| Route | Methods | Auth | Request | Data |
|---|---|---|---|---|
| `/family` | GET, POST | bearer | POST `name` 1–100. Requires plan `familyMembers >= 2`; **409** if already in a family | GET: `family{id,name,myRole}\|null`, `members[{id, email, role, status, isCurrentUser}]`. POST 201: `family{id, name, ownerEmail, myRole:'owner'}, members: []` |
| `/family/invitations` | POST | bearer, owner only | `email`, `role:'editor'\|'viewer'` (default `editor`) | 201: `invitation{email, role, status:'pending', delivered}` |
| `/family/accept` | POST | bearer | `token` (36-char UUID), `accept` — literal `true` accepts, anything else **declines**. TTL 7 days → **`410` with code `RESOURCE_NOT_FOUND`** | `accepted`, `familyId` |
| `/family/members/[memberEmail]` | PATCH, DELETE | bearer, owner only | path = URL-encoded email; PATCH `role:'editor'\|'viewer'` | `updated: true` |

> **Inconsistency:** `POST /family` returns `ownerEmail`; `GET /family` does not. Same
> resource, two shapes. Minor, but Kotlin needs `ownerEmail` nullable or two types.

### Circles

| Route | Methods | Auth | Request | Data |
|---|---|---|---|---|
| `/circles` | GET, POST | bearer + capability `circles` | POST `name` 1–80, `description?` ≤500 | GET: `circles[{id, name, description, role:'owner'\|'member', createdAt}]`. POST 201 `circle{…}` |
| `/circles/[id]` | GET, PATCH, POST, DELETE | bearer; non-UUID id → `404`; non-owner limited to GET | PATCH `name, description`. POST `action:'invite'` (`email`, `displayName?`) \| `'remove_member'` (`email`) \| `'share'` (`interviewIds[]`) | GET: `circle{…}, members[{id, memberEmail, displayName, status, invitedAt, joinedAt}], sharedInterviewIds[], sharedMemories[{id, subjectName, subjectRelation, lifeDecade, messageCount, narrative, createdAt, updatedAt}], deliveredLetters[{id, subject, recipientName, body, hasAudio, unlockAt, createdAt}]`. POST: `invited:true` \| `removed:true` \| `sharedInterviewIds[]`. DELETE `deleted:true` |
| `/circles/accept` | POST | bearer | `token` (36-char UUID), `accept` literal `true` | `accepted`, `circleId` |

`GET /circles/[id]` returns `deliveredLetters[].body` in full. Per plan §6, revoked Circle
access must evict this content locally — so it must be stored in the encrypted,
account-namespaced cache, never in a plain Room column.

### Billing — hosted-web authority

| Route | Methods | Auth | Notes |
|---|---|---|---|
| `/billing/offers` | GET | bearer\|web | Retired compatibility response: `offers:[], catalogStatus:'retired'` |
| `/billing/actions` | POST | bearer\|web | Retired; always `410 GONE` after authentication. It cannot execute a legacy regional/monthly action |
| `/billing/promotions/preview` | POST | bearer\|web | Retired; always `410 GONE` after authentication. It cannot quote a legacy regional/monthly price |
| `/billing/artifacts` | GET | bearer\|web | RL 30/60 s |
| `/billing/artifacts/[reference]/download` | GET | bearer\|web | **RAW PDF bytes.** RL 20/60 s. Errors use the JSON envelope |
| `/billing/recovery-session` | POST | bearer\|web | RL 10/60 s → `{url, returnUrl, purpose:'payment_method_recovery'}` |

**Android decision:** Android calls none of these endpoints. Billing remains an authenticated
web handoff, so retirement of the first three is backward compatible and cannot strand a
native DTO, cache, or pending action. Android must not treat `catalogStatus:'retired'` as an
invitation to fall back to an embedded regional/monthly catalog. Revisit native billing only
if Google Play policy requires it and the product owner separately authorizes that work.

---

## 4. iOS cross-check

`AuthenticationAPI` decodes with `private struct Envelope<T> { let data: T }` and
`FailureEnvelope { error: { code, message } }` — it reads only `data` / `error` and **ignores
`meta` entirely**, so iOS never surfaces `supportId` from the body (it reads the
`X-Support-Id` *header* instead, storing it as `latestSupportID`).

`JSONDecoder.sessionDecoder` sets **only** a custom ISO-8601 date strategy — **no
`keyDecodingStrategy`.** All field names are literal.

iOS request headers: `X-Request-Id` (fresh lowercase UUID), `X-Narratrace-Platform: ios`,
`X-Narratrace-App-Version`, `Cache-Control: no-store`.

**Endpoints iOS calls:** 50, covering every route above except the seven billing routes and
`/auth/link`.

**Endpoints iOS calls with no server route:** none. Every path resolves.

**Server routes iOS never calls:** `/auth/link` + the six `/billing/*` routes.

### Additional mismatches to avoid repeating on Android

1. `interviewShare(id:method:)` passes an arbitrary method string. Server allows only
   `GET`/`POST`/`DELETE`; anything else → `405`. Android: use a sealed type, not a string.
2. `videoPlayback` decodes `/interviews/{id}/messages/{messageId}/media` into `AudioPlayback`.
   That route returns `{url, expiresIn}`; `/letters/{id}/audio` returns
   `{url, mimeType, expiresIn}`. **One Swift type, two server payloads.** Android: two types.
3. `POST /artifact-deliveries` — the defect above.
4. `updateNotificationPreference` sends raw snake_case keys. Correct, but it is the sole
   exception in the API.
5. `refresh` returns a stray `ok: true`.

---

## 5. Implications for the Android build

- **One `NarratraceApiClient`** over OkHttp with an envelope-aware converter. Success and
  error envelopes are uniform enough that a single `ApiResult<T>` sealed type covers all
  routes except the four raw-body endpoints (interview message audio, letter audio POST,
  interview audio-response POST, billing PDF).
- **Unknown enum values must not throw.** `state`, `kind`, `role`, `status`, and
  `ApiErrorCode` will all gain members. Use `@Serializable` with explicit fallbacks.
- **`403 FORBIDDEN` from a family permission is a different UX from `401`.** 401 → re-admit.
  403 → explain the family role limitation. Do not collapse them.
- **`428` is a flow, not an error.** Route to legal acceptance and resume.
- **`202` on media is "still processing."**
- **Only `preservationAcknowledgement` authorises local deletion.**
- **Refresh exactly once per 401, then fail closed** (plan §4). Access TTL is 15 min, so
  this fires often; the refresh mutex must be correct or you get token-rotation races that
  log users out. Single-flight this in the OkHttp `Authenticator`.
