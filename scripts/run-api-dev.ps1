$ErrorActionPreference = "Stop"

$ApiRoot = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$EnvFile = Join-Path $ApiRoot ".env.local"

function Import-DotEnv {
  param([string] $Path)

  if (-not (Test-Path $Path)) {
    return
  }

  Get-Content $Path | ForEach-Object {
    $line = $_.Trim()
    if ($line.Length -eq 0 -or $line.StartsWith("#") -or -not $line.Contains("=")) {
      return
    }

    $parts = $line.Split("=", 2)
    $name = $parts[0].Trim()
    $value = $parts[1].Trim()

    if ($value.Length -ge 2 -and (
      ($value.StartsWith('"') -and $value.EndsWith('"')) -or
      ($value.StartsWith("'") -and $value.EndsWith("'"))
    )) {
      $value = $value.Substring(1, $value.Length - 2)
    }

    [Environment]::SetEnvironmentVariable($name, $value, "Process")
  }
}

Import-DotEnv $EnvFile

if (-not $env:SPRING_PROFILES_ACTIVE) {
  $env:SPRING_PROFILES_ACTIVE = "local"
}

$PreferredJavaHome = "C:\Users\User\.jdks\openjdk-26.0.2"
if ((-not $env:JAVA_HOME) -and (Test-Path $PreferredJavaHome)) {
  $env:JAVA_HOME = $PreferredJavaHome
}

if ($env:JAVA_HOME) {
  $env:Path = "$env:JAVA_HOME\bin;$env:Path"
}

$ServerPort = $env:SERVER_PORT
if (-not $ServerPort) {
  $ServerPort = "8080"
}

$PortInUse = Get-NetTCPConnection -LocalPort ([int]$ServerPort) -State Listen -ErrorAction SilentlyContinue
if ($PortInUse) {
  Write-Host "turn-api is already running on http://127.0.0.1:$ServerPort"
  Write-Host "Close the old turn-api terminal first if you want to restart it."
  return
}

Set-Location $ApiRoot

$MavenRepo = "C:\Users\User\.m2\repository"
if (-not (Test-Path (Split-Path -Parent $MavenRepo))) {
  $MavenRepo = Join-Path $ApiRoot ".m2\repository"
}

Write-Host "Starting turn-api on port $ServerPort..."
& ".\mvnw.cmd" "-Dmaven.repo.local=$MavenRepo" spring-boot:run
