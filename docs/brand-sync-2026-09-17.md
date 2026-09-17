# Android brand synchronization — 17 September 2026

Authorized synchronization follows verified customer-web main `ae78bf9939a7ac9287da9dd529e3b11c841a6ece` and marketing main `3fcc962b95c85c85b84f1689cd463af5ad6f30a7`.

## Changes and scope

The launch uses the authoritative 1200px core WebP derivative (symbol + NARRATRACE) with its intrinsic proportions and ContentScale.Fit, followed by live, scalable `STORIES THAT MATTER.` text. The enclosing scroll container preserves reachability at large font sizes and in landscape. The existing logo-first launch sequence, photographic welcome, and browser callback bypass remain unchanged.

Shared native wordmarks on admission, new-user welcome, and Reception now compose the existing symbol with live NARRATRACE text. The decorative symbol has no separate accessibility announcement; the live name inherits text scaling and active theme colors. The launch core image announces Narratrace; its tagline remains separate accessible text. No native full-lockup placement existed; no unnecessary new screen was introduced. The OS application icon retains its legible symbol-only artwork.

The bundled core asset matches the production derivative SHA-256 `a01fd61ab618bc7b07be13822e6a1f0d22ccf33273e11305ee7698d72b082a49`. Source scan finds no obsolete capture/treasure tagline. No API, authentication, authorization, delivery, storage, billing, Partner feature, signing, or store-release configuration changes.

## Exit gates

Security: static bundled resources and UI text only; no new dependencies, permissions, inputs, data exposure, secret access, network calls, event handlers, or changes to trust boundaries. Existing API and server domain contracts remain backward compatible. Authentication and sign-in before capture remain intact.

Code Quality: `testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest` passes. All 181 unit tests pass with no failures, errors, or skips. Lint reports zero errors and 39 warnings in existing source, bitmap locations, and dependency/toolchain declarations; no warning concerns the new core asset or brand text. The existing Pixel_8 emulator passes all 8 instrumentation tests, including exact accessible name/tagline, logo-before-photograph ordering, and tagline reachability at 200% font scaling. The launch screenshot was visually inspected: the complete core image and readable tagline are centered and uncropped. `assembleRelease --offline` also passes, including R8 minification, resource shrinking, and release vital lint. Debug and unsigned release APKs contain no Markdown. `git diff --check` passes. No documentation is stored in packaged resource or assets directories.

The emulator was the runtime verification target; physical-device and real TalkBack speech review remain unverified. No Play Store submission is performed. Downstream iOS reconciliation is coordinated by the primary workstream; Admin and operations backend contracts are unchanged.
