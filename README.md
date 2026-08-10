# Narratrace Android

Native Kotlin and Jetpack Compose implementation of the Narratrace user experience.

Narratrace Admin and Operations are web-only and are intentionally excluded from this application.

## Security posture

- The application fails closed when identity, integrity, authorization, or API compatibility cannot be verified.
- Cleartext network traffic and Android backup/device-transfer extraction are disabled.
- Protected media will use file-backed streaming and remain local until the backend confirms durable storage and integrity.
- Secrets, signing material, and production API configuration must never be committed.

## Local requirements

- JDK 17
- Android SDK 36
- Gradle 8.13

The current machine still requires installation/configuration of JDK 17 and the Android SDK before the first build can run.

