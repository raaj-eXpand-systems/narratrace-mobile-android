# Narratrace Android — R8 / ProGuard rules
#
# Referenced by app/build.gradle.kts (release buildType, isMinifyEnabled = true).
# The file was missing, so `:app:assembleRelease` failed before it could start.
#
# Policy for this file:
#   - Add rules only for dependencies actually present in app/build.gradle.kts.
#   - Never add a blanket `-keep class **` to make a crash go away. That defeats
#     shrinking and, worse, keeps symbol names that make the release build easier
#     to reverse. Narratrace holds people's private memories; the release binary
#     should give up as little structure as possible.
#   - Every rule below states why it exists.

# ── Crash reporting ──────────────────────────────────────────────────────────
# Keep line numbers so obfuscated stack traces stay actionable, but hide the
# original source file name.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Runtime annotations and generic signatures are required for reflection-based
# serialization and for Compose tooling.
-keepattributes RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations
-keepattributes AnnotationDefault
-keepattributes Signature
-keepattributes InnerClasses,EnclosingMethod

# ── kotlinx.serialization ────────────────────────────────────────────────────
# The compiler plugin generates a `Companion.serializer()` and a `$$serializer`
# for every @Serializable type. R8 cannot see these are reachable, so without
# these rules API response models fail to deserialize in release only — the
# worst possible place to discover it.
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}
-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    *** Companion;
}
-keepclasseswithmembers class ** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Serializer lookup for enums declared @Serializable.
-keepclassmembers @kotlinx.serialization.Serializable class ** {
    *** INSTANCE;
}

# ── Kotlin coroutines ────────────────────────────────────────────────────────
# ServiceLoader-based dispatcher discovery; the entries are not statically
# reachable. The volatile field rule avoids an R8 optimisation that breaks
# atomic state updates inside the coroutine machinery.
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}
-dontwarn kotlinx.coroutines.**

# ── Jetpack Compose ──────────────────────────────────────────────────────────
# Compose ships consumer rules with the AAR; nothing extra is required here.
# Left as a marker so nobody adds a speculative -keep for it later.

# ── Narratrace ───────────────────────────────────────────────────────────────
# No application classes are kept by name. If a future phase needs a keep rule,
# add it here with a comment explaining what breaks without it — and prefer
# fixing the reflection instead.

# Fail loudly rather than silently shipping a binary with unresolved references.
# Deliberately NOT using -ignorewarnings: an unresolved reference in a release
# build is a real defect, and suppressing the warning only moves the discovery
# to a member's device. Add a targeted -dontwarn for a specific package when a
# dependency legitimately references an absent optional class, never a blanket one.
-printconfiguration build/outputs/mapping/full-r8-config.txt
