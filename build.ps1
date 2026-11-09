$ErrorActionPreference = 'Stop'

$java = & java -version 2>&1 | Out-String
if ($java -notmatch '17\.') {
    throw 'JDK 17 is required. Configure JAVA_HOME to JDK 17 before building.'
}
if (-not (Get-Command gradle -ErrorAction SilentlyContinue)) {
    throw 'Gradle 8.9 is not on PATH. Open the project in Android Studio or install Gradle 8.9.'
}

gradle --no-daemon :app:assembleDebug
if ($LASTEXITCODE -ne 0) { throw 'Gradle build failed.' }

$apk = Join-Path $PSScriptRoot 'app\build\outputs\apk\debug\app-debug.apk'
if (-not (Test-Path $apk)) { throw 'APK output was not found.' }
Get-FileHash $apk -Algorithm SHA256
Write-Host "APK: $apk"
