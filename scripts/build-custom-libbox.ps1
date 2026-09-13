[CmdletBinding()]
param(
    [string]$DependenciesRoot = "D:\Temp\sfa-rebuild-deps",
    [switch]$Force
)

$ErrorActionPreference = "Stop"

$repositoryRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot ".."))
$buildRoot = [System.IO.Path]::GetFullPath((Join-Path $repositoryRoot "build\custom-libbox"))
$sourceDirectory = [System.IO.Path]::GetFullPath((Join-Path $buildRoot "sing-box"))
$expectedSourcePrefix = $buildRoot.TrimEnd([System.IO.Path]::DirectorySeparatorChar) + [System.IO.Path]::DirectorySeparatorChar
if (-not $sourceDirectory.StartsWith($expectedSourcePrefix, [System.StringComparison]::OrdinalIgnoreCase)) {
    throw "Unsafe source directory: $sourceDirectory"
}

if (Test-Path -LiteralPath $sourceDirectory) {
    if (-not $Force) {
        throw "Build source already exists. Re-run with -Force to recreate $sourceDirectory"
    }
    Remove-Item -LiteralPath $sourceDirectory -Recurse -Force
}
New-Item -ItemType Directory -Path $buildRoot -Force | Out-Null

$javaHomePath = $env:JAVA_HOME
if ([string]::IsNullOrWhiteSpace($javaHomePath) -and (Test-Path -LiteralPath (Join-Path $DependenciesRoot "jdk"))) {
    $javaHomePath = Get-ChildItem (Join-Path $DependenciesRoot "jdk") -Directory |
        Select-Object -First 1 -ExpandProperty FullName
}
if ([string]::IsNullOrWhiteSpace($javaHomePath) -or -not (Test-Path -LiteralPath (Join-Path $javaHomePath "bin\javac.exe"))) {
    throw "OpenJDK 17 was not found. Set JAVA_HOME or pass -DependenciesRoot."
}

$androidSdkPath = $env:ANDROID_SDK_ROOT
if ([string]::IsNullOrWhiteSpace($androidSdkPath)) {
    $androidSdkPath = $env:ANDROID_HOME
}
if ([string]::IsNullOrWhiteSpace($androidSdkPath)) {
    $androidSdkPath = Join-Path $DependenciesRoot "android-sdk"
}
$androidNdkPath = $env:ANDROID_NDK_HOME
if ([string]::IsNullOrWhiteSpace($androidNdkPath)) {
    $androidNdkPath = Join-Path $androidSdkPath "ndk\28.0.13004108"
}
if (-not (Test-Path -LiteralPath $androidNdkPath)) {
    throw "Android NDK r28 was not found. Set ANDROID_NDK_HOME or pass -DependenciesRoot."
}

$goExecutable = Get-ChildItem -Path (Join-Path $DependenciesRoot "gomodcache\golang.org\toolchain@*\bin\go.exe") -ErrorAction SilentlyContinue |
    Select-Object -First 1 -ExpandProperty FullName
if ([string]::IsNullOrWhiteSpace($goExecutable)) {
    $goExecutable = (Get-Command go -ErrorAction Stop).Source
}
$goBinPath = Split-Path $goExecutable
$goPath = if (Test-Path -LiteralPath (Join-Path $DependenciesRoot "gopath")) {
    Join-Path $DependenciesRoot "gopath"
} else {
    Join-Path $buildRoot "gopath"
}
$goModuleCache = if (Test-Path -LiteralPath (Join-Path $DependenciesRoot "gomodcache")) {
    Join-Path $DependenciesRoot "gomodcache"
} else {
    Join-Path $buildRoot "gomodcache"
}
New-Item -ItemType Directory -Path (Join-Path $goPath "bin") -Force | Out-Null
New-Item -ItemType Directory -Path $goModuleCache -Force | Out-Null

$env:JAVA_HOME = $javaHomePath
$env:ANDROID_HOME = $androidSdkPath
$env:ANDROID_SDK_ROOT = $androidSdkPath
$env:ANDROID_NDK_HOME = $androidNdkPath
$env:GOPATH = $goPath
$env:GOMODCACHE = $goModuleCache
$goVersionLine = Get-Content -LiteralPath (Join-Path $repositoryRoot "version.properties") |
    Select-String -Pattern '^GO_VERSION=(go[0-9]+\.[0-9]+\.[0-9]+)\s*$' |
    Select-Object -First 1
if ($null -eq $goVersionLine) {
    throw "GO_VERSION is missing or invalid in version.properties"
}
$requiredGoVersion = $goVersionLine.Matches[0].Groups[1].Value
$env:GOTOOLCHAIN = $requiredGoVersion
# The official proxy is pinned here because mirrors can lag behind new
# pseudo-versions such as the cronet-go builds required by sing-box.
$env:GOPROXY = "https://proxy.golang.org,direct"
if ([string]::IsNullOrWhiteSpace($env:GOSUMDB) -or $env:GOSUMDB -eq "off") {
    $env:GOSUMDB = "sum.golang.org"
}
$toolchainRoot = & $goExecutable env GOROOT
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($toolchainRoot)) {
    throw "Unable to resolve required Go toolchain $requiredGoVersion"
}
$goBinPath = Join-Path $toolchainRoot.Trim() "bin"
$goExecutable = Join-Path $goBinPath "go.exe"
$actualGoVersion = & $goExecutable version
if ($LASTEXITCODE -ne 0 -or $actualGoVersion -notlike "go version $requiredGoVersion *") {
    throw "Unexpected Go toolchain: $actualGoVersion"
}
$env:GOTOOLCHAIN = "local"
$env:PATH = "$javaHomePath\bin;$goBinPath;$(Join-Path $goPath 'bin');$env:PATH"
Write-Host "Go toolchain: $actualGoVersion"

$coreCommit = "93fff5954390367dd456cad3cbd79be54f8b941f"
$coreVersionTag = "v1.15.0-alpha.3"
$coreFetchDepth = 64
$expectedCoreDescription = "v1.15.0-alpha.3"
git init $sourceDirectory
if ($LASTEXITCODE -ne 0) { throw "Unable to initialize the sing-box source tree" }
git -C $sourceDirectory remote add origin https://github.com/SagerNet/sing-box.git
if ($LASTEXITCODE -ne 0) { throw "Unable to configure the sing-box source remote" }
git -C $sourceDirectory fetch --depth $coreFetchDepth origin $coreCommit "refs/tags/${coreVersionTag}:refs/tags/${coreVersionTag}"
if ($LASTEXITCODE -ne 0) { throw "Unable to fetch sing-box core $coreCommit" }
git -C $sourceDirectory checkout --detach FETCH_HEAD
if ($LASTEXITCODE -ne 0) { throw "Unable to check out sing-box core $coreCommit" }
$actualCoreCommit = (git -C $sourceDirectory rev-parse HEAD).Trim()
if ($LASTEXITCODE -ne 0 -or $actualCoreCommit -ne $coreCommit) {
    throw "Unexpected sing-box core commit: $actualCoreCommit"
}
$actualCoreDescription = (git -C $sourceDirectory describe --tags --abbrev=7).Trim()
if ($LASTEXITCODE -ne 0 -or $actualCoreDescription -ne $expectedCoreDescription) {
    throw "Unexpected sing-box core version: $actualCoreDescription"
}
Write-Host "Core source: $actualCoreDescription"
$versionHeading = Get-Content -LiteralPath (Join-Path $sourceDirectory "docs\changelog.md") |
    Select-String -Pattern '^#### ([0-9]+\.[0-9]+\.[0-9]+(?:-[0-9A-Za-z.-]+)?)\s*$' |
    Select-Object -First 1
if ($null -eq $versionHeading) {
    throw "Unable to read the core release version from the pinned source changelog"
}
$baseCoreVersion = $versionHeading.Matches[0].Groups[1].Value
$shortCoreCommit = $actualCoreCommit.Substring(0, 7)
$embeddedCoreVersion = if ($actualCoreDescription -eq $coreVersionTag) {
    $baseCoreVersion
} else {
    "$baseCoreVersion-$shortCoreCommit"
}
if ($embeddedCoreVersion -notmatch '^[0-9A-Za-z.-]+$') {
    throw "Unsafe embedded core version: $embeddedCoreVersion"
}
Write-Host "Embedded core version: $embeddedCoreVersion"

$patches = @(
    "sing-box-rtt-delay-test.patch",
    "sing-box-rtt-mode-sync.patch",
    "sing-box-rtt-startup-mode.patch",
    "sing-box-profile-cache-isolation.patch"
)
foreach ($patchName in $patches) {
    $patchPath = Join-Path $repositoryRoot "config\patches\$patchName"
    git -C $sourceDirectory apply --check $patchPath
    if ($LASTEXITCODE -ne 0) { throw "Patch check failed: $patchName" }
    git -C $sourceDirectory apply $patchPath
    if ($LASTEXITCODE -ne 0) { throw "Patch apply failed: $patchName" }
}

$versionOverridePath = Join-Path $sourceDirectory "constant\version_sfa.go"
$versionTestPath = Join-Path $sourceDirectory "constant\version_sfa_test.go"
$utf8WithoutBom = [System.Text.UTF8Encoding]::new($false)
$versionOverrideSource = @"
package constant

func init() {
	Version = "$embeddedCoreVersion"
}
"@
$versionTestSource = @"
package constant

import "testing"

func TestSFAEmbeddedVersion(t *testing.T) {
	if Version != "$embeddedCoreVersion" {
		t.Fatalf("unexpected embedded version: %q", Version)
	}
}
"@
[System.IO.File]::WriteAllText($versionOverridePath, $versionOverrideSource, $utf8WithoutBom)
[System.IO.File]::WriteAllText($versionTestPath, $versionTestSource, $utf8WithoutBom)
$gofmtExecutable = Join-Path $goBinPath "gofmt.exe"
& $gofmtExecutable -w $versionOverridePath $versionTestPath
if ($LASTEXITCODE -ne 0) { throw "Unable to format generated core version sources" }

$gomobilePath = Join-Path $goPath "bin\gomobile.exe"
$gobindPath = Join-Path $goPath "bin\gobind.exe"
if (-not (Test-Path -LiteralPath $gomobilePath)) {
    & $goExecutable install github.com/sagernet/gomobile/cmd/gomobile@v0.1.13
    if ($LASTEXITCODE -ne 0) { throw "Unable to install gomobile" }
}
if (-not (Test-Path -LiteralPath $gobindPath)) {
    & $goExecutable install github.com/sagernet/gomobile/cmd/gobind@v0.1.13
    if ($LASTEXITCODE -ne 0) { throw "Unable to install gobind" }
}

Push-Location $sourceDirectory
try {
    & $goExecutable test ./constant ./common/urltest ./experimental/cachefile ./protocol/group ./daemon
    if ($LASTEXITCODE -ne 0) { throw "Core tests failed" }
    & $goExecutable run ./cmd/internal/build_libbox -target android -platform android/arm64
    if ($LASTEXITCODE -ne 0) { throw "libbox build failed" }
} finally {
    Pop-Location
}

$outputDirectory = Join-Path $repositoryRoot "app\libs"
New-Item -ItemType Directory -Path $outputDirectory -Force | Out-Null
$sourceAar = Join-Path $sourceDirectory "libbox.aar"
$targetAar = Join-Path $outputDirectory "libbox.aar"
Copy-Item -LiteralPath $sourceAar -Destination $targetAar -Force

$aar = Get-Item -LiteralPath $targetAar
$hash = Get-FileHash -Algorithm SHA256 -LiteralPath $targetAar
Write-Host "Built $($aar.FullName)"
Write-Host "Size: $($aar.Length) bytes"
Write-Host "SHA-256: $($hash.Hash)"
