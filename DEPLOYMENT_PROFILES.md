# Deployment profilləri

Backend üçün eyni Docker image bütün mühitlərdə istifadə olunur. Secretlər image-ə yazılmır, container başladılarkən environment dəyişənləri ilə verilir.

## Local

`SPRING_PROFILES_ACTIVE=local` olduqda `.env.development` oxunur, Kapital test API-si və memory rate limit işləyir. PostgreSQL və Redis-i istəyə görə belə qaldırmaq olar:

```bash
docker compose -f compose.local.yml up -d
```

## Stage

`stage` GitHub environment yaradın. Backend image `stage` branch push ediləndə `ghcr.io/<owner>/<repo>:stage` kimi hazırlanır. Runtime-da aşağıdakı secret və variable-ları verin:

- `SPRING_PROFILES_ACTIVE=stage`
- `DB_CONNECTION_IP`, `DB_CONNECTION_PORT`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD`, `DB_SSL_MODE`
- `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD`, `REDIS_SSL_ENABLED`
- `APP_JWT_SECRET`, `ADMIN_USERNAME`, `ADMIN_PASSWORD_HASH`
- `APP_ALLOWED_ORIGINS`, `APP_PAYMENT_CALLBACK_BASE_URL`
- `BIRBANK_API_BASE_URL=https://txpgtst.kapitalbank.az/api`
- `BIRBANK_USERNAME`, `BIRBANK_PASSWORD`

## Prod

`prod` profilində tətbiq təhlükəli konfiqurasiya ilə başlamır. HTTPS origin/callback, real Kapital URL-i, unikal JWT secret, dəyişdirilmiş BCrypt admin şifrəsi, `live` payment və TLS-li Redis məcburidir.

- `SPRING_PROFILES_ACTIVE=prod`
- `APP_ENV=prod`
- `APP_PAYMENT_MODE=live`
- `APP_PAYMENT_PROVIDER=abb`
- ABB Business test payment credentials and account details are supplied through
  `ABB_*` environment variables. They must remain in the deployment secret and
  must not be committed to Git.
- `APP_ALLOWED_ORIGINS=https://app.example.az`
- `APP_PAYMENT_CALLBACK_BASE_URL=https://app.example.az`
- `BIRBANK_API_BASE_URL=<bankın verdiyi real production URL>`
- `BIRBANK_USERNAME`, `BIRBANK_PASSWORD`
- `APP_JWT_SECRET=<ən azı 32 simvolluq random secret>`
- `ADMIN_USERNAME=<admin-dən fərqli ad>`
- `ADMIN_PASSWORD_HASH=<BCrypt hash>`
- `APP_RATE_LIMIT_STORE=redis`
- `REDIS_SSL_ENABLED=true`

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
