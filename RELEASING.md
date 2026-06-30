# Releasing

This guide covers signing, cutting a release, and publishing the Chingford
Mosque Prayer Times app to Google Play.

## 1. Generate an upload keystore

Google Play uses [Play App Signing](https://support.google.com/googleplay/android-developer/answer/9842756),
so you only need an **upload key**. Generate one once and keep it safe:

```bash
keytool -genkeypair -v -keystore upload.jks -keyalg RSA -keysize 2048 -validity 10000 -alias upload
```

You will be prompted for a keystore password, a key password, and your
identity details. Record the passwords securely — they are needed for every
release. **Never commit `upload.jks` or its passwords to the repository.**

## 2. Configure GitHub repository secrets

The release workflow (`.github/workflows/release.yml`) signs builds using
secrets configured under **Settings → Secrets and variables → Actions**:

| Secret | Value |
| --- | --- |
| `KEYSTORE_BASE64` | The keystore file, base64-encoded: `base64 -w0 upload.jks` |
| `KEYSTORE_PASSWORD` | The keystore password chosen above |
| `KEY_ALIAS` | The key alias (e.g. `upload`) |
| `KEY_PASSWORD` | The key password chosen above |

Generate the base64 string with:

```bash
base64 -w0 upload.jks
```

(on macOS use `base64 -i upload.jks | tr -d '\n'`)

The workflow decodes `KEYSTORE_BASE64` into `$RUNNER_TEMP/upload.jks` and
exports `CM_KEYSTORE_PATH`, `CM_KEYSTORE_PASSWORD`, `CM_KEY_ALIAS`, and
`CM_KEY_PASSWORD` for the Gradle build. **If the secrets are absent the build
still succeeds**, but it falls back to debug signing and the resulting AAB/APK
is **NOT uploadable to Google Play** — it is only useful for testing.

## 3. Local release builds

For a release build on your own machine, create a `keystore.properties` file in
the repository root (it is gitignored and must never be committed):

```properties
storeFile=/absolute/path/to/upload.jks
storePassword=your-keystore-password
keyAlias=upload
keyPassword=your-key-password
```

Then build:

```bash
./gradlew :app:bundleRelease :app:assembleRelease
```

The release signing config reads `keystore.properties` if it exists, otherwise
it reads the `CM_KEYSTORE_PATH`, `CM_KEYSTORE_PASSWORD`, `CM_KEY_ALIAS`, and
`CM_KEY_PASSWORD` environment variables, otherwise it falls back to debug
signing.

Outputs:

- `app/build/outputs/bundle/release/*.aab`
- `app/build/outputs/apk/release/*.apk`

## 4. Cut a release

Releases are produced by the `Release` workflow, which triggers on tags
matching `v*` (and can be run manually via **workflow_dispatch**):

```bash
git tag v1.2.3
git push origin v1.2.3
```

The workflow runs `:core` tests, builds and signs the AAB and APK, uploads them
as workflow artifacts, and attaches them to a GitHub Release named after the
tag (or `app-v<run_number>` for a manual dispatch).

> **versionCode must increase on every Play upload.** The build supports
> `CM_VERSION_CODE` and `CM_VERSION_NAME` environment-variable overrides if you
> need to set them explicitly for a release.

## 5. Publish to Google Play

1. **Enroll in Play App Signing** when you first create the app in the Play
   Console. Upload your upload key (or let Google generate the app signing key).
2. **Upload the `.aab`** from the release to a Play Console track (internal →
   closed → production).
3. **Declare the `SCHEDULE_EXACT_ALARM` permission.** The app uses exact alarms
   to fire the adhan at precise prayer times, so complete the Play Console
   permission declaration explaining this use.
4. **Add the privacy policy URL** — point it at the hosted version of
   [`PRIVACY.md`](PRIVACY.md).
5. **Complete the store listing**: app icon (512×512), screenshots, and a
   feature graphic.
6. **Fill in the Data Safety form.** The app collects no personal data — it only
   fetches publicly published prayer times and stores settings/cache locally on
   the device. Declare accordingly (no data collected, no data shared).
