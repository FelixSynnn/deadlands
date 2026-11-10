<#
.SYNOPSIS
  Runs Gradle for DEADLANDS with a JDK new enough for Fabric Loom (25+).

.DESCRIPTION
  Loom 1.19 must run on Java 25 or newer, but Minecraft 1.21.11 and the mod itself use Java 21
  (build.gradle pins a Java 21 toolchain for compiling and for running the game). This script
  finds an installed JDK 25+ and runs gradlew with it, so you never have to change your system
  JAVA_HOME. Set DEADLANDS_JAVA_HOME to force a specific JDK.

.EXAMPLE
  .\dev.ps1 build                 # compile + run the server game tests + produce the jar
  .\dev.ps1 runClient             # play the development client
  .\dev.ps1 runServer             # start a development dedicated server
  .\dev.ps1 runClientGameTest     # automated end-to-end client test with screenshots
#>
param([Parameter(ValueFromRemainingArguments = $true)][string[]]$GradleArgs)
$ErrorActionPreference = 'Stop'

function Get-JavaMajor([string]$javaHome) {
	$release = Join-Path $javaHome 'release'
	if (Test-Path $release) {
		$match = Select-String -Path $release -Pattern '^JAVA_VERSION="(\d+)' | Select-Object -First 1
		if ($match) { return [int]$match.Matches[0].Groups[1].Value }
	}
	return 0
}

$candidates = @()
if ($env:DEADLANDS_JAVA_HOME) { $candidates += $env:DEADLANDS_JAVA_HOME }
if ($env:JAVA_HOME) { $candidates += $env:JAVA_HOME }
foreach ($root in @('C:\Program Files\Java', 'C:\Program Files\Eclipse Adoptium', 'C:\Program Files\Microsoft',
		'C:\Program Files\Zulu', 'C:\Program Files\Amazon Corretto', "$env:USERPROFILE\.jdks")) {
	if (Test-Path $root) {
		$candidates += Get-ChildItem $root -Directory | Sort-Object Name -Descending | ForEach-Object FullName
	}
}

$jdk = $candidates | Where-Object { (Test-Path (Join-Path $_ 'bin\java.exe')) -and ((Get-JavaMajor $_) -ge 25) } | Select-Object -First 1
if (-not $jdk) {
	throw "No JDK 25 or newer was found. Install one (for example: winget install EclipseAdoptium.Temurin.25.JDK) or set DEADLANDS_JAVA_HOME."
}

$env:JAVA_HOME = $jdk
Write-Host "DEADLANDS: running Gradle on JDK $(Get-JavaMajor $jdk) ($jdk); the mod compiles and runs on Java 21." -ForegroundColor DarkGray
if (-not $GradleArgs) { $GradleArgs = @('build') }
& (Join-Path $PSScriptRoot 'gradlew.bat') @GradleArgs
exit $LASTEXITCODE
