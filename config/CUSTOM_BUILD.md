# Custom Traffic build

## Custom libbox

The Traffic variant uses a patched ARM64 sing-box v1.14.0 core. Rebuild it from a clean source tree with:

```powershell
.\scripts\build-custom-libbox.ps1 -Force
```

The script clones the pinned tag, applies the RTT patches in order, runs the relevant Go tests, builds `libbox.aar`, and copies it to `app/libs`.

OpenJDK 17, Android SDK, Android NDK r28, Git, and Go are required. Pass `-DependenciesRoot` if the local tools are not stored in `D:\Temp\sfa-rebuild-deps`.

## Traffic signing

`traffic-signing.properties` and `config/signing/traffic.keystore` are private and ignored by Git. To preserve compatibility with an already installed debug-signed Traffic build while making its signing key durable, run:

```powershell
$env:JAVA_HOME = "C:\path\to\jdk17"
.\scripts\setup-traffic-signing.ps1
```

Back up both generated files together. If they are lost, future APKs cannot update an installed Traffic build. When the private files are absent, local Traffic builds fall back to the Android debug signing key.
