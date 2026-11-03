# Contributing to LofiQ

Thank you for your interest in contributing to **LofiQ**! We welcome bug fixes, architecture improvements, UI enhancements, and new feature implementations from the open-source community.

---

## Code of Conduct

Be welcoming, polite, and constructive. We strive to maintain a collaborative and harassment-free environment for all contributors.

---

## Getting Started

1. **Fork the Repository:** Create your personal fork on GitHub.
2. **Clone your Fork:**
   ```bash
   git clone https://github.com/itsafxal/LofiQ-Music.git
   cd LofiQ-Music
   ```
3. **Configure Local Environment:**
   - Copy `local.properties.example` to `local.properties`.
   - Ensure `sdk.dir` points to your local Android SDK.
   - Set up your Firebase project by copying `app/google-services.json.example` to `app/google-services.json` with your project's credentials.
4. **Create a Feature Branch:**
   ```bash
   git checkout -b feature/amazing-feature
   # or
   git checkout -b fix/issue-description
   ```

---

## Security & Architecture Guidelines

When contributing code to LofiQ, please adhere strictly to these rules:

### 1. No Secrets or Personal Credentials in Code
- **Never** hardcode API secrets, private keys, service account JSON files, or personal session cookies.
- Use `local.properties` and `BuildConfig` for local credentials that must be kept out of version control.
- Ensure all new secret file types are listed in `.gitignore`.

### 2. Preserve Embedded Client Architecture
- Do **NOT** introduce mandatory VPS backends or centralized proxy servers.
- Preserve the direct, on-device audio resolution and client-side playback pipeline.

### 3. Session & Token Isolation
- Store authentication cookies and access tokens exclusively in private `PreferenceDataStore` instances.
- Never log raw tokens, cookies, or headers via `Log` or `LofiQLogger`.
- Never upload user sessions to Cloud Firestore or external analytics.

### 4. Firestore Security Rules
- If you introduce a new Firestore collection or document model, you **MUST** update `firestore.rules` to enforce authentication, owner validation, and default deny.
- Update `test/firestore_rules_test.js` and verify that all security rules unit tests pass.

---

## Coding Standards

- **Language:** Idiomatic Kotlin with Kotlin 2.0+ and Jetpack Compose.
- **Architecture:** Unidirectional Data Flow (UDF), ViewModel + StateFlow, Repository pattern with Dagger Hilt.
- **Dependency Injection:** Use `@Inject` constructor injection. Avoid manual singleton creation.
- **Logging:** Use `LofiQLogger` with appropriate category tags (`Category.PLAYBACK`, `Category.DATABASE`, etc.). Never log PII.

---

## Verification Before Submitting

Before submitting a Pull Request, verify that your changes compile and all tests pass:

```bash
# 1. Run all unit tests
./gradlew testReleaseUnitTest

# 2. Run Firestore Security Rules tests
node test/firestore_rules_test.js

# 3. Assemble release APK to verify R8/ProGuard and packaging
./gradlew assembleRelease
```

---

## Submitting a Pull Request

1. Push your branch to your GitHub fork:
   ```bash
   git push origin feature/amazing-feature
   ```
2. Open a Pull Request against the `main` branch of the upstream repository.
3. Provide a clear PR description explaining:
   - What problem is solved or feature added.
   - Any architectural changes or new dependencies.
   - Proof of test results.
4. Maintainers will review your PR and provide constructive feedback.

