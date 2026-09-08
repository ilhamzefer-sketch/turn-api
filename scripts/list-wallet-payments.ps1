$ErrorActionPreference = "Stop"

$ApiRoot = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$ScriptRoot = Join-Path $ApiRoot "scripts"

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

java -cp $JdbcJar (Join-Path $ScriptRoot "ListWalletPayments.java")
