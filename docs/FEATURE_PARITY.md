# NOIR / DaFit Canvas feature parity checklist

**Audit date:** 2026-10-11  
**Target:** NoiseFit Twist Go Elite Edition 5, NSW-309; observed label MOYOUNG-V2, BLE name `NF 1_5D45`, software `JLQFN0CA1.0` / `MOY-BOU2-2.1.1`.  
**Reviewed source:** upstream `e-labInnovations/DaFit-Canvas` commit `1d5fc3f810fc7e3be62aef3f40318f825e4fbc61`, fetched and audited from Git. `vendor/dafit-canvas/` contains only package metadata, the generated dependency lock, and provenance record; CI fetches only that immutable commit for packaging.  
**Source archive:** No uploaded DaFit Canvas ZIP was present in `/home/ubuntu/upload` or `/home/ubuntu/Downloads`; the public upstream Git source was accessible and was audited at the pinned commit. The upstream repository contains no license file, so its full source was not copied into this public NOIR repository. Confirm redistribution rights before publishing APKs containing the source-derived bundle. This checklist describes the source actually audited, not a missing ZIP.

## Status definitions

- **Source/offline:** source code implements this capability; its use from this APK or on NSW-309 is not thereby proven.
- **Native adapter present, unverified:** an Android/WebView bridge exists, but has not passed callback-order, failure-injection, or hardware tests.
- **Not verified / blocked:** do not represent as operational for this firmware until tested on the physical watch.
- **N/A:** capability is absent from the audited website or cannot be honestly inferred from it.

## Module-by-module inventory

| DaFit Canvas source module/capability | NOIR port/module | Status | Evidence / limitation |
|---|---|---|---|
| `src/App.tsx`, `src/main.tsx`, `src/App.css`, `src/index.css` routing, global shell, styles | `app/src/main/assets/dafit/` generated web bundle; `MainActivity.kt` WebView host | Source/offline; Android runtime build was previously CI-built | CI run `38124315525` succeeded at upstream app commit `06d28db`; APK artifact contained the app build. Current Android WebView shell loads local `file:///android_asset/dafit/index.html`. No emulator launch test available. |
| Home, About, route navigation: `pages/Home.tsx`, `AboutModal.tsx` | Bundled website | Source/offline | Available in upstream UI; actual offline launch still requires local APK smoke test. |
| Watch connection/device info: `lib/moyoungBle.ts`, `pages/WatchConsole.tsx` | `NativeBleBridge.kt`, `noir-ble-shim.js` | Native adapter present, unverified | JS shim covers currently observed APIs (requestDevice, connect, services/characteristics, reads/writes, notifications, disconnect events); Android code does not yet serialize all async GATT ops or resolve write/CCCD promises on actual callbacks. Selection UI uses a JS prompt and only partially honors filters. |
| Protocol framing/opcodes/notifications: `lib/moyoungProtocol.ts`, `types/moyoung.ts` | `core/MoYoungProtocol.kt`, `core/MoYoungFeatures.kt` | Partial duplicated port; NSW-309 writes unverified | Source has 0xFEEA framing and command helpers; Kotlin has several corresponding helpers. No parity test suite/test vectors or exact firmware proof. No checksum is defined by the audited source for ordinary commands. |
| MOYOUNG device identification, manufacturer/software/battery reads | `NativeBleBridge.kt`; source connection path | Not verified | Website requires manufacturer characteristic to equal `MOYOUNG-V2` and reads 0x2A28/0x2A19. User-reported GATT data is evidence but runtime discovery/read on target is still required; some characteristics may be absent. |
| Face Type C/DAWFT parser, writer, validation: `lib/dawft.ts` | Pinned source build / bundled web app | Source/offline; tests/hardware unverified | Type C parse/pack paths are in source. Full native conversion is not present. |
| FaceN parser/writer, JSON and bitmap packing: `lib/faceN.ts` | Pinned source build / bundled web app | Source/offline; tests/hardware unverified | Parser/serializer/packer paths are present. Editor labels some FaceN features as coming soon; do not infer full parity for those controls. |
| BMP and bitmap pixels: `lib/bmp.ts`, `lib/assetThumb.ts`, `components/editor/BmpPixelEditor.tsx` | Pinned source build / bundled web app | Source/offline | Depends on browser Canvas/Blob/File APIs supported by WebView; Android testing not performed. |
| Type C and FaceN face rendering/preview: `lib/renderFace.ts`, `lib/renderFaceN.ts`, preview components | Pinned source build / bundled web app | Source/offline | Preview is software rendering, not a validation of target watch rendering. |
| Project import/export, ZIP, metadata, binary dump/inspection: `lib/projectIO.ts`, `pages/Pack.tsx`, `pages/Dump.tsx` | Pinned source build / bundled web app | Source/offline | Uses JSZip and browser file/download APIs; APK runtime file-picker/share workflow needs testing. |
| Face editor, store, layers, properties, canvas, asset import, font generator, pixel editor: `pages/Editor.tsx`, `store/editorStore.ts`, `components/editor/*`, `lib/fontLoader.ts`, `lib/glyphRasterizer.ts` | Pinned source build / bundled web app | Source/offline; broad mobile usability unverified | Source includes substantial Type C and FaceN editing. Feature availability is format-specific; not all object types or operations are common to both formats. Google Fonts fetches remote resources when requested. |
| Built-in catalogue and remote MoYoung API: `pages/WatchFaces.tsx`, `lib/moyoung.ts` | Pinned source build / bundled web app | Remote dependency; runtime availability/lawfulness/terms not verified | API uses `/api/moyoung` and `/api/moyoung-cdn` endpoints; inspect deployment/proxy requirements. Catalogue may not work from Android `file://` origin. No local catalog snapshot is claimed. |
| Face upload preparation, chunking, acknowledgements and apply: `lib/moyoungBle.ts`, upload pages/dialog | Web Bluetooth shim + NativeBleBridge | **Blocked pending safe Android bridge and physical NSW-309 verification** | Source assumes file slot 0x74, 244-byte chunks, FE EA preparation/ready/complete notifications, checksum in reply, then apply command. Existing native bridge resolves GATT writes on request acceptance, not operation callbacks; “upload success” cannot prove the face changed. Must not enable/claim a verified upload. |
| Watch console readouts and packet logging: `pages/WatchConsole.tsx` | native bridge emits events; shim delivers read/notify | Partial, unverified | BLE read callback values are separate async events and the shim has partial correlation; logs are not yet a complete, structured, exportable TX/RX diagnostics record. |
| Weather/time/settings/device commands exposed by protocol source | Source protocol helpers and Kotlin `MoYoungFeatures` | Protocol helper/source only; watch actions unverified | Source documents time, weather, notification, health-trigger, goals, units, language, find-phone/watch/media-related opcodes. No target firmware test evidence for any command; avoid asserting feature support based only on shared vendor protocol. |
| Live heart rate, BP, SpO2 and other health data/history | source command/protocol helpers; Kotlin parser handles 0xFF sentinel | Not verified; no truthful history sync demonstrated | A 0xFF response is invalid and Kotlin parser treats it as unavailable. The website source does not establish reliable NSW-309 historical step/sleep/SpO2/BP sync. |
| Phone notification forwarding | `NoirNotificationListener.kt`; native notification command helper | Partial infrastructure; end-to-end forwarding unverified | Android listener permission is required; code stores local notification metadata. Full category mapping, safe transmission lifecycle, and target behavior require testing. |
| Alarms, reminders, DND, brightness, media, find-phone/watch | source command table/console where represented | Not verified | Do not present controls as supported on NSW-309 without an actual confirmed command/result. No complete native controls workflow is evidenced. |
| Firmware update/DFU/bootloader | none | Intentionally excluded | Not present in the app goal; unsafe and outside scope. No firmware writes or speculative bytes. |

## NSW-309 support matrix

| Function | Current evidence | Release claim |
|---|---|---|
| BLE scan, connect, discover | Reported advertisement `NF 1_5D45` and service observations supplied by project owner; code path exists | Candidate only; verify on watch and Android permission/lifecycle path |
| Device info and battery | Standard characteristic UUIDs observed; source reads 0x2A29, 0x2A28, 0x2A19 | Not confirmed on current firmware until an actual read is logged |
| Raw FEEA command/notification | Reported characteristics and one notification sample | Read/notify inspection is safe; unknown writes remain disabled/unverified |
| Time, weather, goals, units/language, notification forwarding, find controls | Shared MoYoung V2/source references | Not verified for exact `JLQFN0CA1.0` / `MOY-BOU2-2.1.1`; protocol similarity is not device confirmation |
| Health measurement/history | Shared opcode support and invalid sentinel parsing | Not verified; must reject 0xFF and other invalid values; no fabricated history |
| Type C/FaceN editing, preview, packing/export | DaFit Canvas source logic | Offline software capability, not proof the watch accepts either format |
| Face upload/change | Source upload state machine targets Gallery slot 0x74 and 244-byte chunks | **Unsupported pending hardware confirmation. Never label successful upload/face change yet.** |

## Build and hardware evidence

- Existing successful upstream workflow run: [Build NOIR Android APK](https://github.com/helloww20000-creator/NOIR/actions/runs/38124315525), source app revision `06d28dbfed86f352946338bf1c092a31869a309c`, artifact `NOIR-debug-apk` (downloaded and verified present, 4,074,269 bytes).
- That workflow fetched the website from an unpinned upstream default branch. It is not a reproducible provenance guarantee. The local rebuild branch changes the workflow to fetch the exact source commit above and use the committed `vendor/dafit-canvas/package-lock.json`.
- Local frontend validation passed: `npm ci --include=dev --legacy-peer-deps --no-audit --no-fund`, `npm run build` (TypeScript `tsc -b` plus Vite), and `npm run lint`. Vite emitted a non-fatal large-chunk warning (733.47 KB JS before gzip).
- Local Android validation passed with JDK 17, Gradle 8.9, Android platform 35 and build-tools 35.0.0: `gradle assembleDebug --stacktrace` completed successfully. `gradle testDebugUnitTest --stacktrace` also completed, but **the repository contains no unit-test sources**, so this is not protocol/adapter test coverage.
- Verified APK: `app/build/outputs/apk/debug/app-debug.apk`, 3.9 MiB, SHA-256 `580a510adb0a84a271481d75dc2044a1d719c46e3aa23f629de33070a5f0804f`. `aapt dump badging` reports package `com.noir.companion.dev`, versionCode 4/versionName `0.2.1-dev`, min SDK 26 and target SDK 35. APK inspection confirms local `assets/dafit/index.html`, `noir-ble-shim.js`, and hashed CSS/JS bundles; `apksigner verify` passed (debug key, APK Signature Scheme v2).
- The dedicated branch commit could not be pushed: GitHub rejected it because the configured GitHub App lacks the `workflows` permission to create/update `.github/workflows/android.yml`. No updated-branch GitHub Actions run or hosted artifact link exists yet. The local APK above is the verified build artifact; the existing run cited above is from the older, unpinned workflow.
- No Android emulator/ADB or physical NSW-309 connection was available; no app launch or watch behavior has been tested.
- Package ID is `com.noir.companion.dev` on the rebuild branch, so it installs alongside the prior `com.noir.companion` package rather than updating it. Debug APK is not a production-signed release.

## Physical device test checklist (required before declaring watch features supported)

1. Install the APK; grant Nearby Devices/Bluetooth permission; verify scan shows the real watch and cancel/retry errors are accurate.
2. Connect, disconnect, reconnect; verify service discovery completes, app background/foreground recovery, and no duplicate/stale connected state.
3. Record the exact discovered UUIDs/properties; read manufacturer/software/battery/device-information only where READ exists.
4. Subscribe to FEE3 and capture raw notification bytes; verify notification-disable/unsubscribe on disconnect.
5. Test time sync only after confirming payload/ack behavior and watch display; check local timezone and date rollover.
6. Trigger one health query only if the source command is verified; ensure 0xFF/sentinel is shown as unavailable, never 255.
7. Do not attempt face upload until the native queue, callback correlation, acknowledgement timeout/retry/cancel semantics are fixed. If later enabled, confirm the visible watch face changed independently of transport completion.
