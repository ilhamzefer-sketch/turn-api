# Deployment profilləri

Backend üçün eyni Docker image bütün mühitlərdə istifadə olunur. Secretlər image-ə yazılmır, container başladılarkən environment dəyişənləri ilə verilir.

## Local

`SPRING_PROFILES_ACTIVE=local` olduqda `.env.development` oxunur. Coin abunəliyi lokal bazada işləyir, bank kartı və payment reconciliation bağlı qalır. PostgreSQL və Redis-i istəyə görə belə qaldırmaq olar:

```bash
docker compose -f compose.local.yml up -d
```

## Stage

`stage` GitHub environment yaradın. Backend image `stage` branch push ediləndə `ghcr.io/<owner>/<repo>:stage` kimi hazırlanır. Runtime-da aşağıdakı secret və variable-ları verin:

- `SPRING_PROFILES_ACTIVE=stage`
- `DB_CONNECTION_IP`, `DB_CONNECTION_PORT`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD`, `DB_SSL_MODE`
- `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD`, `REDIS_SSL_ENABLED`
- `APP_JWT_SECRET`, `ADMIN_USERNAME`, `ADMIN_PASSWORD_HASH`
- `APP_JWT_ISSUER=novbetime-api`, `APP_JWT_AUDIENCE=novbetime-web`
- `APP_ACCESS_TOKEN_MINUTES=10`, `APP_PRIVILEGED_ACCESS_TOKEN_MINUTES=5`
- `APP_SESSION_USER_IDLE_MINUTES=30`, `APP_SESSION_PRIVILEGED_IDLE_MINUTES=15`, `APP_SESSION_ADMIN_IDLE_MINUTES=10`
- `APP_SESSION_USER_ABSOLUTE_HOURS=12`, `APP_SESSION_PRIVILEGED_ABSOLUTE_HOURS=8`, `APP_SESSION_ADMIN_ABSOLUTE_HOURS=4`
- `APP_ALLOWED_ORIGINS`
- `APP_LEGACY_API_ENABLED=false`, `APP_PAYMENT_RECONCILIATION_ENABLED=false`
- `APP_WALLET_COINS_PER_AZN=10`, `APP_WALLET_WHATSAPP_URL=https://wa.me/message/P63GI5XJ3PQLC1`

## Prod

`prod` profilində tətbiq təhlükəli konfiqurasiya ilə başlamır. Subscription ödənişləri yalnız coin balansından çıxılır; legacy bank API və payment reconciliation production-da qadağandır. HTTPS origin, unikal JWT secret, dəyişdirilmiş BCrypt admin şifrəsi və TLS-li Redis məcburidir.

- `SPRING_PROFILES_ACTIVE=prod`
- `APP_ENV=prod`
- `APP_LEGACY_API_ENABLED=false`
- `APP_PAYMENT_RECONCILIATION_ENABLED=false`
- `APP_WALLET_COINS_PER_AZN=10`
- `APP_WALLET_WHATSAPP_URL=https://wa.me/message/P63GI5XJ3PQLC1`
- `APP_ALLOWED_ORIGINS=https://app.example.az`
- `APP_JWT_SECRET=<ən azı 32 simvolluq random secret>`
- `ADMIN_USERNAME=<admin-dən fərqli ad>`
- `ADMIN_PASSWORD_HASH=<BCrypt hash>`
- `APP_RATE_LIMIT_STORE=redis`
- `REDIS_SSL_ENABLED=true`

Sessiya limitləri server tərəfindən tətbiq olunur. Refresh rotasiyası inactivity və absolute deadline-ları uzatmır. Background polling istifadəçi fəaliyyəti sayılmır; yalnız frontend-in real interaction heartbeat-i idle deadline-ı absolute deadline həddinə qədər yeniləyir.

JWT secret yaratmaq:

```bash
openssl rand -base64 48
```

Admin BCrypt hash yaratmaq:

```bash
docker run --rm httpd:2.4-alpine htpasswd -bnBC 12 "" 'STRONG_PASSWORD' | tr -d ':\n'
```

## Frontend

Frontend image build zamanı yalnız public dəyişənlər qəbul edir. GitHub-da `stage` və `prod` environment-lərində `VITE_API_BASE_URL` variable yaradın. Secret frontend build-ə verilməməlidir.

## Monitorinq

- Liveness: `http://<management-host>:9090/actuator/health/liveness`
- Readiness: `http://<management-host>:9090/actuator/health/readiness`
- Prometheus: `http://<management-host>:9090/actuator/prometheus`

Management portunu yalnız daxili şəbəkədə açın. İctimai ingress-ə qoşmayın.

Coin sistemi üçün mərhələli yayımlama, məlumat bütövlüyü, smoke test və geri dönüş qaydaları
[`COIN_RELEASE_CHECKLIST.md`](COIN_RELEASE_CHECKLIST.md) sənədində verilib.
