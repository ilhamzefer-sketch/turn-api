$ErrorActionPreference = "Stop"

$env:JAVA_HOME = "C:\Users\User\.jdks\openjdk-26.0.2"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"

$env:SPRING_PROFILES_ACTIVE = "local"
$env:SERVER_PORT = "8082"
$env:MANAGEMENT_SERVER_PORT = "9092"

$env:DB_CONNECTION_IP = "127.0.0.1"
$env:DB_CONNECTION_PORT = "55432"
$env:DB_NAME = "turn_epoint_test"
$env:DB_USERNAME = "turn_epoint_test"
$env:DB_PASSWORD = "turn_epoint_test_password"

$env:REDIS_HOST = "127.0.0.1"
$env:REDIS_PORT = "56379"
$env:APP_RATE_LIMIT_STORE = "memory"
$env:APP_ALLOWED_ORIGINS = "http://localhost:5275,http://127.0.0.1:5275,http://localhost:5276,http://127.0.0.1:5276,http://localhost:5173,http://127.0.0.1:5173"

$env:APP_PAYMENT_PROVIDER = "epoint"
$env:APP_PAYMENT_MODE = "sandbox"
$env:APP_PAYMENT_CALLBACK_BASE_URL = "http://host.docker.internal:8082"
$env:EPOINT_PUBLIC_KEY = "i000000001"
$env:EPOINT_PRIVATE_KEY = "sandbox_private_key_0000000001"
$env:EPOINT_API_BASE_URL = "http://127.0.0.1:8181/api/1"
$env:EPOINT_SUCCESS_URL = "http://127.0.0.1:5276/app/wallet?payment=success"
$env:EPOINT_ERROR_URL = "http://127.0.0.1:5276/app/wallet?payment=failed"
$env:EPOINT_RESULT_URL = "http://host.docker.internal:8082/api/payments/epoint/callback"
$env:EPOINT_LANGUAGE = "az"
$env:EPOINT_CURRENCY = "AZN"
$env:EPOINT_SANDBOX_ENABLED = "true"

$mvn = Get-ChildItem "C:\Users\User\.m2\wrapper\dists\apache-maven-3.9.16" -Recurse -Filter mvn.cmd |
  Select-Object -First 1 -ExpandProperty FullName

Set-Location "C:\Users\User\Desktop\turn-api"
& $mvn -o "-Dmaven.repo.local=C:\Users\User\Desktop\turn-api\.m2\repository" spring-boot:run
