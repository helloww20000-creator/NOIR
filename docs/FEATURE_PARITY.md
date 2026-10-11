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
| Face upload preparation, chunking, acknowledgements and apply: `lib/moyoungBle.ts`, upload pages/dialog | Web Bluetooth shim + `WatchFaceUploadGuard` | **Native safety-blocked; exact device compatibility unverified** | Source assumes file slot 0x74, 244-byte chunks, FE EA preparation/ready/complete notifications, checksum reply, then apply command. Native Android bridge rejects the exact Gallery preparation packet, apply packet, and all FEE6 file chunks before sending them, with a surfaced explanatory error. Upload control remains visible in source UI but cannot transfer these bytes through the native bridge. This prevents an unverified upload from being falsely reported as successful; it does not validate device behavior. |
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
| Raw FEEA command/notification | Reported characteristics and one notification sample; four local protocol/guard unit tests pass | Read/notify inspection is implemented; only the exact unverified gallery upload paths are blocked. Other writes are not thereby hardware-verified. |
| Time, weather, goals, units/language, notification forwarding, find controls | Shared MoYoung V2/source references | Not verified for exact `JLQFN0CA1.0` / `MOY-BOU2-2.1.1`; protocol similarity is not device confirmation |
| Health measurement/history | Shared opcode support and invalid sentinel parsing | Not verified; must reject 0xFF and other invalid values; no fabricated history |
| Type C/FaceN editing, preview, packing/export | DaFit Canvas source logic | Offline software capability, not proof the watch accepts either format |
| Face upload/change | Source upload state machine targets Gallery slot 0x74 and 244-byte chunks | **Unsupported pending hardware confirmation. Never label successful upload/face change yet.** |

## Build and hardware evidence

- Historical run [38124315525](https://github.com/helloww20000-creator/NOIR/actions/runs/38124315525) fetched DaFit Canvas from an unpinned default branch and is not a provenance guarantee. The current branch workflow fetches the exact reviewed source commit above and uses the committed `vendor/dafit-canvas/package-lock.json`.
- Local frontend validation passed: `npm ci --include=dev --legacy-peer-deps --no-audit --no-fund`, `npm run build` (TypeScript `tsc -b` plus Vite), and `npm run lint`. Vite emitted a non-fatal large-chunk warning (733.47 KB JS before gzip).
- Local Android validation passed with JDK 17, Gradle 8.9, Android platform 35 and build-tools 35.0.0: `gradle testDebugUnitTest assembleDebug --stacktrace` completed successfully. Four JUnit tests cover command framing, setting endianness/truncated input, the 0xFF health sentinel, and blocking the exact unverified gallery upload packets/chunks. These do not substitute for BLE callback-order, malformed face-file, or hardware tests.
- Final test-enabled CI run: [38125571246](https://github.com/helloww20000-creator/NOIR/actions/runs/38125571246), commit `784ba15c5176d7f380db81b31915ac5af08a4b64`. Frontend build, four-test Gradle task, Android APK assembly, in-APK asset verification, and artifact upload all passed.
- Final verified artifact: [`NOIR-debug-apk` (artifact 11697840111)](https://github.com/helloww20000-creator/NOIR/actions/runs/38125571246/artifacts/11697840111). Downloaded APK size is 4,074,269 bytes; SHA-256 `c696b3ddb10604f186ab2467463f09379343f72dec5a405f3957ce4684470bd9`. APK metadata: package `com.noir.companion.dev`, versionCode 4/versionName `0.2.1-dev`, min SDK 26, target SDK 35. Contents include the local `assets/dafit/index.html`, BLE shim, and built CSS/JS. APK signature verifies with debug key / APK Signature Scheme v2.
- CI and local unit tests cover packet framing, byte order/truncated input, 0xFF measurement sentinel handling, and the face-upload guard (four passing tests). They do not cover BLE callback ordering, malformed face-file parser cases, or real device responses.
- The branch push initially required the owner to grant GitHub workflow permission; after that, branch [`manus/complete-dafit-rebuild`](https://github.com/helloww20000-creator/NOIR/tree/manus/complete-dafit-rebuild) was pushed and CI completed successfully.
- No Android emulator/ADB or physical NSW-309 connection was available; no app launch or watch behavior has been tested.
- Package ID is `com.noir.companion.dev` on the rebuild branch, so it installs alongside the prior `com.noir.companion` package rather than updating it. Debug APK is not a production-signed release.
- Debug-signing certificate fingerprints differed across observed CI runs (`2898bdf6…` for run 38125157301 versus `820cafe7…` for run 38125571246). A later CI APK may therefore fail in-place update. If Android reports a signature mismatch, uninstall only the old `com.noir.companion.dev` instance (this deletes its local data), then install the new APK; do not uninstall `com.noir.companion`.

## Physical device test checklist (required before declaring watch features supported)

1. Install the APK; grant Nearby Devices/Bluetooth permission; verify scan shows the real watch and cancel/retry errors are accurate.
2. Connect, disconnect, reconnect; verify service discovery completes, app background/foreground recovery, and no duplicate/stale connected state.
3. Record the exact discovered UUIDs/properties; read manufacturer/software/battery/device-information only where READ exists.
4. Subscribe to FEE3 and capture raw notification bytes; verify notification-disable/unsubscribe on disconnect.
5. Test time sync only after confirming payload/ack behavior and watch display; check local timezone and date rollover.
6. Trigger one health query only if the source command is verified; ensure 0xFF/sentinel is shown as unavailable, never 255.
7. Do not attempt face upload until the native queue, callback correlation, acknowledgement timeout/retry/cancel semantics are fixed. If later enabled, confirm the visible watch face changed independently of transport completion.
