# Custom Traffic build

## Custom libbox

The Traffic variant uses a patched ARM64 sing-box core pinned to official testing commit `b84b42bc72dd7fad73ee1b3b65bfddf864eacf1b`, paired with Android upstream commit `ec05af8afa066b87025784355590a802168a0e07` (1.15.0-alpha.2). The build reads the release version from the pinned core changelog and appends the commit hash, then generates a source-level fallback so gomobile cannot leave it as `unknown`. The current result is `1.15.0-alpha.2-b84b42b`; the source ancestry is verified against `v1.14.0-52-gb84b42b` because the core does not yet have a 1.15.0-alpha.2 Git tag. Rebuild it from a clean source tree with:

```powershell
.\scripts\build-custom-libbox.ps1 -Force
```

The script fetches the pinned commit, applies the custom patches in order, runs the relevant Go tests, builds `libbox.aar`, and copies it to `app/libs`. When RTT mode is enabled, the core performs a one-time RTT pass over selector members 500 milliseconds after startup with up to 20 concurrent checks; nested URL test groups keep their own health checks. Only Selector state is isolated by Android profile ID, while each configuration's normal `cache_id` remains untouched. Legacy shared or profile-wide cache namespaces are deliberately ignored.

OpenJDK 17, Android SDK, Android NDK r28, Git, and Go are required. Pass `-DependenciesRoot` if the local tools are not stored in `D:\Temp\sfa-rebuild-deps`.

The core now defaults to the `go` TUN stack when `stack` is omitted; explicit stack settings in profiles are preserved. The existing close-connections switch applies to both node selection and Clash mode changes. Connections are closed only after a different mode is successfully selected.

## Traffic signing

`traffic-signing.properties` and `config/signing/traffic.keystore` are private and ignored by Git. To preserve compatibility with an already installed debug-signed Traffic build while making its signing key durable, run:

```powershell
$env:JAVA_HOME = "C:\path\to\jdk17"
.\scripts\setup-traffic-signing.ps1
```

Back up both generated files together. If they are lost, future APKs cannot update an installed Traffic build. When the private files are absent, local Traffic builds fall back to the Android debug signing key.
