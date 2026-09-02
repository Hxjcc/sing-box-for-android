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
$env:GOTOOLCHAIN = "local"
if ([string]::IsNullOrWhiteSpace($env:GOSUMDB) -or $env:GOSUMDB -eq "off") {
    $env:GOSUMDB = "sum.golang.org"
}
$env:PATH = "$javaHomePath\bin;$goBinPath;$(Join-Path $goPath 'bin');$env:PATH"

git clone --depth 1 --branch v1.14.0 https://github.com/SagerNet/sing-box.git $sourceDirectory
if ($LASTEXITCODE -ne 0) { throw "Unable to clone sing-box v1.14.0" }

$patches = @(
    "sing-box-rtt-delay-test.patch",
    "sing-box-rtt-mode-sync.patch",
    "sing-box-rtt-startup-mode.patch"
)
foreach ($patchName in $patches) {
    $patchPath = Join-Path $repositoryRoot "config\patches\$patchName"
    git -C $sourceDirectory apply --check $patchPath
    if ($LASTEXITCODE -ne 0) { throw "Patch check failed: $patchName" }
    git -C $sourceDirectory apply $patchPath
    if ($LASTEXITCODE -ne 0) { throw "Patch apply failed: $patchName" }
}

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
    & $goExecutable test ./common/urltest ./protocol/group ./daemon
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
