# Custom Traffic build

## Custom libbox

The Traffic variant uses a patched ARM64 sing-box core pinned to official testing commit `b609f959f57ce34416c51c7b87ce4a76f2e1df56`, paired with Android upstream commit `3295b6b35811ba71df6363d6c87bc180acc2e3b9` (1.15.0-alpha.8). The build reads the release version from the pinned core changelog, appends the commit hash for untagged commits, and generates a source-level fallback so gomobile cannot leave it as `unknown`. The current result is `1.15.0-alpha.8`, verified against the official `v1.15.0-alpha.8` tag. Rebuild it from a clean source tree with:

```powershell
.\scripts\build-custom-libbox.ps1 -Force
```

The script fetches the pinned commit, applies the custom patches in order, runs the relevant Go tests, builds `libbox.aar`, and copies it to `app/libs`. When RTT mode is enabled, the core performs a one-time RTT pass over selector members 500 milliseconds after startup with up to 20 concurrent checks; nested URL test groups keep their own health checks. Only Selector state is isolated by Android profile ID, while each configuration's normal `cache_id` remains untouched. Legacy shared or profile-wide cache namespaces are deliberately ignored.

OpenJDK 17, Android SDK, Android NDK r28, Git, and Go are required. The script resolves and verifies the Go version declared in `version.properties` (currently Go 1.26.8), downloading the toolchain through the official Go proxy if needed. Pass `-DependenciesRoot` if the local tools are not stored in `D:\Temp\sfa-rebuild-deps`.

The core uses sing-tun's own TCP/IP stack when `stack` is omitted. The `stack` option is deprecated in 1.15.0 and scheduled for removal in 1.17.0; the app preserves explicit settings in profiles and displays the core's migration notice. The Android alpha.6 merge moves blocking I/O off the main thread while preserving profile-specific selector caches. The existing close-connections switch applies to both node selection and Clash mode changes. Connections are closed only after a different mode is successfully selected.

The upstream libbox build no longer enables `with_gvisor`. Profiles explicitly selecting `mixed` or `gvisor` must remove `stack` before starting the service; omitting it selects the built-in Go stack.

App and core must be upgraded together from alpha.7 onwards. The Android alpha.8 code calls two libbox APIs that only exist in the matching core: `Libbox.hasTunInbound(configContent)` for VPN service detection in `Settings.kt`, and `BoxNetworkInterface.dnsSearchDomain` for reporting interface search domains in `PlatformInterfaceWrapper.kt`. Core alpha.8 also replaces the `OutboundGroup.Now()` binding with `Selected(network)` / `AttachConnection(closer)` and adds `dns_server_address` / `dns_search_domain` rule items, none of which require further Kotlin changes.

## Memory during reload

The custom `sing-box-reload-memory.patch` closes the old instance in a non-inlined helper, removing its stack references before the existing pre-reload garbage collection. Simply assigning nil was insufficient in the regression test. Previously, subsequent reload checks kept the closed instance live during collection, delaying reclamation until the replacement was constructed. A separate boolean preserves reload behavior, log retention, and power-report classification. The regression test checks that the old instance is unreachable before constructing the next outbound across repeated reloads; it fails against the previous implementation and passes with this patch.

The Android app also renders cached application icons at the list's displayed 40 dp size instead of retaining full-size launcher bitmaps. The dashboard receives `memory.Total()` from core alpha.8; on Android this is process RSS, including the UI, native allocations and Go runtime, rather than Go heap alone. A transient peak that later falls does not by itself establish a leak. Device-specific baseline usage and peak reduction require measurements on the user's phone and profile.

## Personal build without privileged integrations

The personal Android build removes the LSPosed/Xposed module, its provider and metadata, privileged settings/log screens, root Binder services, libsu, root package queries and root installation. Automatic redirect is no longer offered and Android override options always disable it, so an old saved setting cannot re-enable it. Stored installer names outside the supported system/Shizuku methods fall back to the system installer. Shizuku and normal VPN per-app routing remain available. Root-only bridge, neighbor monitoring, SFTP lookup and switching local shell users are no longer implemented; the libbox-required platform methods report unsupported capabilities. The ordinary app-UID shell remains available.

Daily memory work defers installed-app component metadata until scanning, serializes scan sessions and limits APK decoding to two concurrent jobs. The log UI subscribes only while visible, and both displayed and paused log buffers retain at most 3,000 entries each. Oversized incoming batches and pause/resume merging are covered by JVM unit tests. Dashboard status/group subscriptions are suspended when no dashboard, groups or connections surface needs the shared client. These changes retain the previous reload-memory core patch; reductions in RSS must still be measured on-device.

## First-open responsiveness

The home Connections bottom sheet renders a fixed-height loading shell during its entrance animation. It waits for the actual expanded, non-animating sheet state and one frame before creating its connection content and registering the visibility subscription. Connection state collection and details navigation live inside the sheet instead of the activity, avoiding whole-dashboard recomposition on every connection snapshot. Closing before expansion cancels the pending load without registering a subscription. Device frame-time verification is still required.

The app picker now resolves labels only for the filtered list on a cancellable background sorting job, and loads visible 40 dp icons asynchronously with at most two decoder workers. Connection rows serialize and recheck icon cache misses so repeated connections from one package share one decode, render icons at 32 dp, and cap the bitmap cache at 2 MiB. Unused connection-row app labels are no longer loaded. Bitmaps are prepared before publication. These address main-thread resource loading and redundant first-open icon work; no device frame-time measurements have been taken.

## Traffic signing

`traffic-signing.properties` and `config/signing/traffic.keystore` are private and ignored by Git. To preserve compatibility with an already installed debug-signed Traffic build while making its signing key durable, run:

```powershell
$env:JAVA_HOME = "C:\path\to\jdk17"
.\scripts\setup-traffic-signing.ps1
```

Back up both generated files together. If they are lost, future APKs cannot update an installed Traffic build. When the private files are absent, local Traffic builds fall back to the Android debug signing key.
