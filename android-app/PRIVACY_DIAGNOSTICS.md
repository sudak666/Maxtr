# Privacy-safe diagnostics policy

- Production diagnostics contain only a closed feature-domain enum and stable error code.
- Never log or upload exception messages, stack traces, tokens, amounts, comments, profile identifiers, document paths, emails or user-entered text.
- Crashlytics is intentionally not linked while the app has no explicit diagnostics consent flow. Adding it requires opt-in consent, disabled collection by default, a deletion path and tests proving payload redaction before any console enablement.
- User-visible errors are localized resource identifiers; retryability comes from `SyncFailure`, never raw backend text.
- `DiagnosticsPrivacyTest` prevents raw exception-message/stack/Crashlytics recording APIs from entering production Kotlin sources.
