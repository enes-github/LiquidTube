# Releasing LiquidTube

LiquidTube updates itself from GitHub Releases — there's no app store. When you publish
a new release, every phone already running a build with the updater (0.3.2 and up) sees
an "Update available" notification and an in-app sheet, and installs the new APK in place.

## One-time setup

### 1. Signing key (critical)

Android only installs an update if it's signed with the **same key** as the app already
on the phone. Use one keystore for every release, forever. If you sign a later release
with a different key, people have to uninstall and reinstall by hand.

If you don't have a keystore yet:

```bash
keytool -genkey -v -keystore release.keystore -alias liquidtube \
  -keyalg RSA -keysize 2048 -validity 10000
```

Keep `release.keystore` safe and backed up. Losing it means no more seamless updates.

### 2. Repository secrets

In GitHub → **Settings → Secrets and variables → Actions**, add:

| Secret | Value |
| --- | --- |
| `KEYSTORE_BASE64` | the keystore, base64-encoded (see below) |
| `KEYSTORE_PASSWORD` | the keystore (store) password |
| `KEY_ALIAS` | the key alias (e.g. `liquidtube`) |
| `KEY_PASSWORD` | the key password |

Encode the keystore:

```bash
base64 -w0 release.keystore > keystore.b64    # Linux
base64 -i release.keystore | tr -d '\n' > keystore.b64   # macOS
```

Paste the contents of `keystore.b64` into `KEYSTORE_BASE64`.

### 3. Bootstrap the current users (do this once)

Phones running an **older** build (0.3.1 and earlier) have no updater in them, so they
can't be reached from inside the app — that's not fixable retroactively. Share the first
updater build (0.3.2) with everyone the way you do now (send them the GitHub link). Once
they're on it, every future release updates itself and nobody falls behind again.

Make sure that bootstrap APK is signed with the **same keystore** the workflow uses.

## Cutting a release

1. Bump the version in `app/build.gradle.kts`:
   - `versionName` — the human version, e.g. `0.3.3` (users see this).
   - `versionCode` — bump by 1 every release (Android's internal counter).
2. Commit.
3. Tag it to match `versionName` and push the tag:

   ```bash
   git tag v0.3.3
   git push origin v0.3.3
   ```

The **Release** workflow (`.github/workflows/release.yml`) then builds a signed APK and
publishes it as the latest GitHub Release with auto-generated notes. The release notes
become the "what's new" text in the in-app update sheet.

> The workflow checks that the tag matches `versionName` and fails loudly if they differ,
> so a forgotten bump can't ship a mislabelled release.

## How the in-app updater works

- On launch and every ~12 hours in the background, the app reads
  `https://api.github.com/repos/enes-github/LiquidTube/releases/latest`.
- If the release's version is higher than the installed one, it raises a quiet
  notification and shows the update sheet.
- **Update** downloads the release's `.apk` asset and hands it to Android's installer.
  The first time, Android asks the user to allow installs from LiquidTube.

Users can turn automatic checks off, or check manually, in **Account → Settings → Updates**.
