# Builds InGamePPT without Gradle: fetches paper-api (once) into .tools/libs,
# compiles with javac and packages a plugin jar into build/.
param(
    [string]$Version = "0.1.0",
    [string]$PaperApi = "26.1.2.build.74-stable"
)

$ErrorActionPreference = "Stop"
$root = $PSScriptRoot
$libs = Join-Path $root ".tools\libs"
$classes = Join-Path $root "out\plugin"
$staging = Join-Path $root "out\jar"
$base = "https://repo.papermc.io/repository/maven-public"

New-Item -ItemType Directory -Force -Path $libs, $classes, $staging, (Join-Path $root "build") | Out-Null

$dependencies = @(
    "io/papermc/paper/paper-api/$PaperApi/paper-api-$PaperApi.jar",
    "com/google/guava/guava/33.5.0-jre/guava-33.5.0-jre.jar",
    "com/google/guava/failureaccess/1.0.2/failureaccess-1.0.2.jar",
    "com/google/code/gson/gson/2.13.2/gson-2.13.2.jar",
    "org/yaml/snakeyaml/2.2/snakeyaml-2.2.jar",
    "org/joml/joml/1.10.8/joml-1.10.8.jar",
    "it/unimi/dsi/fastutil/8.5.18/fastutil-8.5.18.jar",
    "org/apache/logging/log4j/log4j-api/2.25.2/log4j-api-2.25.2.jar",
    "org/slf4j/slf4j-api/2.0.17/slf4j-api-2.0.17.jar",
    "com/mojang/brigadier/1.3.10/brigadier-1.3.10.jar",
    "net/md-5/bungeecord-chat/1.21-R0.2-deprecated%2Bbuild.21/bungeecord-chat-1.21-R0.2-deprecated+build.21.jar",
    "org/jspecify/jspecify/1.0.0/jspecify-1.0.0.jar",
    "org/checkerframework/checker-qual/3.49.2/checker-qual-3.49.2.jar",
    "net/kyori/examination-api/1.3.0/examination-api-1.3.0.jar",
    "net/kyori/adventure-api/4.26.1/adventure-api-4.26.1.jar",
    "net/kyori/adventure-key/4.26.1/adventure-key-4.26.1.jar",
    "net/kyori/adventure-text-minimessage/4.26.1/adventure-text-minimessage-4.26.1.jar",
    "net/kyori/adventure-text-serializer-gson/4.26.1/adventure-text-serializer-gson-4.26.1.jar",
    "net/kyori/adventure-text-serializer-legacy/4.26.1/adventure-text-serializer-legacy-4.26.1.jar",
    "net/kyori/adventure-text-serializer-plain/4.26.1/adventure-text-serializer-plain-4.26.1.jar",
    "net/kyori/adventure-text-logger-slf4j/4.26.1/adventure-text-logger-slf4j-4.26.1.jar"
)

foreach ($dependency in $dependencies) {
    $name = Split-Path $dependency -Leaf
    $destination = Join-Path $libs $name
    if (-not (Test-Path $destination)) {
        Write-Host "downloading $name"
        & curl.exe -sS -m 300 -o $destination "$base/$dependency"
    }
}

$sources = Get-ChildItem -Recurse -Filter *.java (Join-Path $root "src\main\java") | ForEach-Object { $_.FullName }
Write-Host "compiling $($sources.Count) source files"
& javac -encoding UTF-8 --release 21 -cp "$libs/*" -d $classes $sources
if ($LASTEXITCODE -ne 0) { throw "javac failed" }

Remove-Item -Recurse -Force $staging -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path $staging | Out-Null
Copy-Item -Recurse -Force (Join-Path $classes "*") $staging

$utf8NoBom = New-Object System.Text.UTF8Encoding($false)
$pluginYml = [System.IO.File]::ReadAllText((Join-Path $root "src\main\resources\plugin.yml"), [System.Text.Encoding]::UTF8).Replace('${version}', $Version)
[System.IO.File]::WriteAllText((Join-Path $staging "plugin.yml"), $pluginYml, $utf8NoBom)
Copy-Item -Force (Join-Path $root "src\main\resources\config.yml") $staging

$jar = Join-Path $root "build\InGamePPT-$Version.jar"
& jar --create --file $jar -C $staging .
if ($LASTEXITCODE -ne 0) { throw "jar failed" }
Write-Host "built $jar"
