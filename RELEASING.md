# Android releases

The `Build and publish Android release` workflow runs when a matching `v*` tag is pushed. It runs the unit tests, builds a signed APK, attaches it to a GitHub Release as `Lumn.apk`, and updates `static/update.json` on `main` so installed apps can discover the release.

## One-time signing setup

Android accepts an in-place app update only when the new APK has the same signing certificate as the installed app. Configure these repository Actions secrets before creating a release tag:

- `LUMN_SIGNING_KEYSTORE_BASE64`: base64-encoded keystore used to sign the existing installed app.
- `LUMN_SIGNING_KEYSTORE_PASSWORD`
- `LUMN_SIGNING_KEY_ALIAS`
- `LUMN_SIGNING_KEY_PASSWORD`

For builds signed with the standard local Android debug key, the usual alias and passwords are `androiddebugkey` and `android`. On macOS, encode the keystore without adding line breaks with `base64 < "$HOME/.android/debug.keystore" | tr -d '\n'`; add the output as the base64 secret in GitHub under **Settings → Secrets and variables → Actions**. Do not commit or paste the keystore into the repository.

Only after the secrets are configured, push the tag matching `versionName` in `app/build.gradle.kts` (for example, `v2.5`). The workflow intentionally stops before publishing if signing secrets are missing.
