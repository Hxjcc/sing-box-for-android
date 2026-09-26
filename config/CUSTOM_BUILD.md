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

## Traffic signing

`traffic-signing.properties` and `config/signing/traffic.keystore` are private and ignored by Git. To preserve compatibility with an already installed debug-signed Traffic build while making its signing key durable, run:

```powershell
$env:JAVA_HOME = "C:\path\to\jdk17"
.\scripts\setup-traffic-signing.ps1
```

Back up both generated files together. If they are lost, future APKs cannot update an installed Traffic build. When the private files are absent, local Traffic builds fall back to the Android debug signing key.
