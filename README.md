# NOIR — DaFit Canvas Android companion

NOIR is an Android WebView host for the locally bundled DaFit Canvas toolkit with a native Android BLE compatibility bridge. The target device is the NoiseFit Twist Go Elite Edition 5 (reported model NSW-309), observed firmware `JLQFN0CA1.0` / `MOY-BOU2-2.1.1`.

## Source and reproducibility

The supplied DaFit Canvas ZIP was not present in the uploaded files. The accessible upstream source was audited at commit [`1d5fc3f810fc7e3be62aef3f40318f825e4fbc61`](https://github.com/e-labInnovations/DaFit-Canvas/commit/1d5fc3f810fc7e3be62aef3f40318f825e4fbc61). CI fetches that exact commit and uses the committed dependency lockfile in `vendor/dafit-canvas/`; it no longer clones a moving upstream default branch. The upstream repository has no license file, so the source itself is not copied into this public repository. Confirm redistribution rights before distributing APKs containing the source-derived bundle.

## Build

GitHub Actions workflow: [Build NOIR Android APK](https://github.com/helloww20000-creator/NOIR/actions/workflows/android.yml). The workflow fetches the pinned frontend, installs from the committed lockfile, bundles it into the APK, assembles a debug APK, verifies the local website and shim assets, and uploads `NOIR-debug-apk`.

For a local build, use JDK 17, Android SDK platform 35/build tools 35.0.0, Gradle 8.9, and Node.js 22.17.1. First follow the pinned `git fetch`/checkout and lockfile-copy commands in the workflow's **Fetch pinned DaFit Canvas source and build frontend** step. Then, in the NOIR repo root, run:

```sh
cd /tmp/dafit-canvas
npm ci --include=dev --legacy-peer-deps --no-audit --no-fund
npm run build
cd /path/to/NOIR
mkdir -p app/src/main/assets/dafit
cp -R /tmp/dafit-canvas/dist/. app/src/main/assets/dafit/
```

Then apply the shim injection step from `.github/workflows/android.yml` and run `gradle assembleDebug`.

## Important compatibility limits

- `com.noir.companion.dev` is the development package ID; the APK installs alongside `com.noir.companion` and does not update it.
- The website/editor/parser/export functions are bundled locally. Catalogue and optional Google Fonts functions may contact external endpoints when invoked.
- The Android Web Bluetooth shim is **not yet verified as a complete/serialized GATT implementation**. Accepted writes are not equivalent to watch callbacks.
- Face upload/change and all commands, health values/history, weather, settings, and notification forwarding need exact-firmware physical-device verification. Type C/FaceN support in the source does not prove the watch accepts those formats.
- The build is debug-signed, not a Play Store/release-signed APK. No emulator or target watch has been used in this environment.

See [`docs/FEATURE_PARITY.md`](docs/FEATURE_PARITY.md) for the source inventory, verified/unverified feature matrix, and hardware checklist. NOIR intentionally sends no speculative protocol bytes and performs no firmware/bootloader operations.
