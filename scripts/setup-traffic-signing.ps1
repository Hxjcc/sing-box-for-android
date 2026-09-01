[CmdletBinding()]
param(
    [string]$SourceKeystore = (Join-Path $env:USERPROFILE ".android\debug.keystore"),
    [string]$SourceStorePassword = "android",
    [string]$SourceAlias = "androiddebugkey",
    [string]$SourceKeyPassword = "android"
)

$ErrorActionPreference = "Stop"
$repositoryRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot ".."))
$destinationDirectory = Join-Path $repositoryRoot "config\signing"
$destinationKeystore = Join-Path $destinationDirectory "traffic.keystore"
$propertiesPath = Join-Path $repositoryRoot "traffic-signing.properties"
if (Test-Path -LiteralPath $destinationKeystore) {
    throw "Traffic keystore already exists: $destinationKeystore"
}
if (Test-Path -LiteralPath $propertiesPath) {
    throw "Traffic signing properties already exist: $propertiesPath"
}
if (-not (Test-Path -LiteralPath $SourceKeystore)) {
    throw "Source keystore was not found: $SourceKeystore"
}

$javaHomePath = $env:JAVA_HOME
if ([string]::IsNullOrWhiteSpace($javaHomePath)) {
    throw "JAVA_HOME must point to OpenJDK 17"
}
$keytool = Join-Path $javaHomePath "bin\keytool.exe"
if (-not (Test-Path -LiteralPath $keytool)) {
    throw "keytool was not found under JAVA_HOME"
}

New-Item -ItemType Directory -Path $destinationDirectory -Force | Out-Null
$passwordBytes = New-Object byte[] 32
[System.Security.Cryptography.RandomNumberGenerator]::Fill($passwordBytes)
$destinationPassword = [Convert]::ToBase64String($passwordBytes).Replace("+", "-").Replace("/", "_").TrimEnd("=")

& $keytool -importkeystore `
    -srckeystore $SourceKeystore `
    -srcstorepass $SourceStorePassword `
    -srcalias $SourceAlias `
    -srckeypass $SourceKeyPassword `
    -destkeystore $destinationKeystore `
    -deststoretype PKCS12 `
    -deststorepass $destinationPassword `
    -destkeypass $destinationPassword `
    -destalias traffic `
    -noprompt
if ($LASTEXITCODE -ne 0) { throw "Unable to create the Traffic keystore" }

$properties = @(
    "storeFile=config/signing/traffic.keystore",
    "storePassword=$destinationPassword",
    "keyAlias=traffic",
    "keyPassword=$destinationPassword"
)
[System.IO.File]::WriteAllLines($propertiesPath, $properties, [System.Text.UTF8Encoding]::new($false))

Write-Host "Traffic signing is configured. Back up both files securely:"
Write-Host "  $destinationKeystore"
Write-Host "  $propertiesPath"
