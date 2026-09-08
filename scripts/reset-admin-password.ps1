$ErrorActionPreference = "Stop"

$ApiRoot = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$ScriptRoot = Join-Path $ApiRoot "scripts"
$AdminUsername = "admin"
$AdminPassword = "NovbeTime2026!Admin"
$AdminPasswordHash = '$2y$10$3Ehrpe5CUmnRa79/msUu3O2mQ.qwbdj.e/zefTCP8wfrqhxwHM/LG'

Set-Location $ApiRoot

$PreferredJavaHome = "C:\Users\User\.jdks\openjdk-26.0.2"
if ((-not $env:JAVA_HOME) -and (Test-Path $PreferredJavaHome)) {
  $env:JAVA_HOME = $PreferredJavaHome
}

if ($env:JAVA_HOME) {
  $env:Path = "$env:JAVA_HOME\bin;$env:Path"
}

$JdbcJar = Get-ChildItem "$ApiRoot\.m2\repository\org\postgresql\postgresql" -Recurse -Filter "postgresql-*.jar" |
  Sort-Object FullName -Descending |
  Select-Object -First 1 -ExpandProperty FullName

if (-not $JdbcJar) {
  throw "PostgreSQL JDBC driver was not found under $ApiRoot\.m2\repository."
}

Write-Host "Resetting local platform admin..."
$env:RESET_ADMIN_USERNAME = $AdminUsername
$env:RESET_ADMIN_PASSWORD_HASH = $AdminPasswordHash
java -cp $JdbcJar (Join-Path $ScriptRoot "ResetAdminPassword.java")
if ($LASTEXITCODE -ne 0) {
  throw "Admin reset failed."
}

Write-Host ""
Write-Host "Admin login reset edildi:"
Write-Host "  URL:   http://127.0.0.1:5275/platform/login"
Write-Host "  Login: $AdminUsername"
Write-Host "  Parol: $AdminPassword"
