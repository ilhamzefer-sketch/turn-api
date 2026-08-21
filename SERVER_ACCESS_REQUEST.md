# Server access and Caddy status

Bu fayl serverde yoxlanilan real veziyyeti, tapilan melumatlari ve hele deqiqlestirilmeli qalanlari yigir.

## Meqsed

- `http://169.58.172.211` yeni frontend-e getsin.
- Frontend daxilindeki `/api` sorgulari movcud backend-e yonlendirilsin.
- Kohne frontend port `80`-den cixarilsin.
- Movcud avtomatik deployment sistemi islemeye davam etsin.
- Deyisiklikden sonra esas sehife, login, qeydiyyat, CSRF ve API baglantisi yoxlanilsin.

## Tapilan server melumatlari

- Server IP: `169.58.172.211`
- SSH user: `root`
- SSH port: `22`
- SSH baglanti: ugurludur
- SSH user icazesi: `root`, sudo lazim deyil
- Hostname: `vmi3506559`
- OS/kernel: Ubuntu Linux, kernel `6.8.0-137-generic`
- Port `22`: aciqdir
- Port `80`: aciqdir
- Port `443`: firewall-da aciqdir, amma Caddy container host-da yalniz `80:80` publish edir
- Port `30081`: firewall-da aciqdir

## Caddy ve Docker veziyyeti

- Caddy system service kimi islemir:
  - `caddy.service` tapilmadi
- Caddy Docker Compose daxilinde isleyir.
- Compose project adi: `ops`
- Compose config fayli:
  - `/opt/turn/turn-api/ops/docker-compose.stage.vps.yml`
- Caddyfile yolu:
  - `/opt/turn/turn-api/ops/Caddyfile`
- Caddy container:
  - `ops-caddy-1`
  - image: `caddy:2.10-alpine`
  - status: `running`
  - host port mapping: `0.0.0.0:80->80/tcp`
- Caddyfile container-e bele mount olunub:
  - host: `/opt/turn/turn-api/ops/Caddyfile`
  - container: `/etc/caddy/Caddyfile`
  - mode: read-only

## Hazirki container statuslari

- `ops-caddy-1`: running
- `ops-ui-1`: healthy
- `ops-api-1`: healthy
- `ops-postgres-1`: healthy
- `ops-redis-1`: healthy

## Hazirki Caddy routing

Hazirki `/opt/turn/turn-api/ops/Caddyfile` mentiqi:

```caddyfile
{$TURN_DOMAIN} {
  encode zstd gzip

  handle /api/* {
    reverse_proxy api:8080
  }

  handle /actuator/* {
    respond "not found" 404
  }

  handle {
    reverse_proxy 169.58.172.211:30081
  }
}
```

Bu o demekdir:

- `/api/*` -> `api:8080`
- `/actuator/*` -> `404`
- diger butun requestler -> `169.58.172.211:30081`

## Hazirki Compose mentiqi

Compose fayli:

- Caddy `./stack.env` env faylindan oxuyur.
- UI build arg:
  - `VITE_API_BASE_URL=${PUBLIC_BASE_URL}`
  - `VITE_APP_ENV=stage`
- API env fayli:
  - `./turn.stage.env`
- Postgres/Redis secret-leri `stack.env`-den gelir.

## Env fayllarinda olan acarlar

Secret deyerleri oxunmadi ve bu fayla yazilmadi. Yalniz acar adlari yoxlanildi.

`/opt/turn/turn-api/ops/stack.env` acarlar:

- `TURN_DOMAIN`
- `PUBLIC_BASE_URL`
- `BACKEND_IMAGE`
- `FRONTEND_IMAGE`
- `POSTGRES_DB`
- `POSTGRES_USER`
- `POSTGRES_PASSWORD`
- `REDIS_PASSWORD`

`/opt/turn/turn-api/ops/turn.stage.env` acarlar:

- `SPRING_PROFILES_ACTIVE`
- `SERVER_PORT`
- `MANAGEMENT_SERVER_PORT`
- `MANAGEMENT_SERVER_ADDRESS`
- `APP_NAME`
- `APP_ENV`
- `DB_CONNECTION_IP`
- `DB_CONNECTION_PORT`
- `DB_NAME`
- `DB_USERNAME`
- `DB_PASSWORD`
- `DB_SSL_MODE`
- `DB_MAX_POOL_SIZE`
- `DB_MIN_IDLE`
- `APP_JPA_DDL_AUTO`
- `APP_JPA_SHOW_SQL`
- `APP_JPA_FORMAT_SQL`
- `APP_JWT_SECRET`
- `APP_ACCESS_TOKEN_MINUTES`
- `APP_REFRESH_TOKEN_DAYS`
- `APP_SECURE_COOKIES`
- `APP_ALLOWED_ORIGINS`
- `APP_TRUST_PROXY_HEADERS`
- `APP_RATE_LIMIT_ENABLED`
- `APP_RATE_LIMIT_GLOBAL_PER_MINUTE`
- `APP_RATE_LIMIT_AUTH_PER_MINUTE`
- `APP_RATE_LIMIT_PAYMENT_PER_MINUTE`
- `APP_RATE_LIMIT_PUBLIC_QUEUE_PER_MINUTE`
- `APP_RATE_LIMIT_STORE`
- `REDIS_HOST`
- `REDIS_PORT`
- `REDIS_PASSWORD`
- `REDIS_SSL_ENABLED`
- `REDIS_CONNECT_TIMEOUT`
- `REDIS_COMMAND_TIMEOUT`
- `ADMIN_USERNAME`
- `ADMIN_PASSWORD_HASH`
- `APP_PAYMENT_MODE`
- `APP_PAYMENT_PROVIDER`
- `APP_PAYMENT_CALLBACK_BASE_URL`
- `BIRBANK_API_BASE_URL`
- `BIRBANK_USERNAME`
- `BIRBANK_PASSWORD`
- `APP_PAYMENT_RECONCILIATION_ENABLED`
- `APP_PAYMENT_RECONCILIATION_DELAY_MS`
- `APP_PAYMENT_RECONCILIATION_INITIAL_DELAY_MS`

## Firewall veziyyeti

`ufw` aktivdir.

Inbound icazeli portlar:

- `22/tcp`
- `80/tcp`
- `443/tcp`
- `30080/tcp`
- `30443/tcp`
- `30081/tcp`
- `30082/tcp`

Default:

- incoming: deny
- outgoing: allow
- routed: deny

Serverde Kubernetes/Docker firewall chain-leri de var.

## Yoxlama neticeleri

- `http://169.58.172.211/` cavab verdi: `200`
- `http://169.58.172.211/` yeni NovbeTime UI title qaytarir.
- `http://169.58.172.211/login` cavab verdi: `200`
- `http://169.58.172.211/register` cavab verdi: `200`
- `http://169.58.172.211/api/auth/csrf` cavab verdi: `200`
- CSRF endpoint JSON qaytarir.
- Bu o demekdir ki, IP -> Caddy -> `30081` yeni UI ve `/api` -> API baglantisi hazirda isleyir.

## Edilen deyisiklik

- `/opt/turn/turn-api/ops/Caddyfile` icinde esas frontend route deyisdirildi:
  - evvel: `reverse_proxy ui:8080`
  - indi: `reverse_proxy 169.58.172.211:30081`
- Caddy config validate edildi.
- Caddy container restart edildi:
  - `ops-caddy-1`
- Docker bind mount inode problemi oldugu ucun reload tek basina kifayet etmedi; restart-dan sonra container yeni Caddyfile-i gordu.

## Backup fayllari

Serverde yaradilan backup fayllari:

- `/opt/turn/turn-api/ops/Caddyfile.backup-current-20260821-121631`
- `/opt/turn/turn-api/ops/Caddyfile.backup-before-30081-20260821-121631`

## Hele deqiqlestirilmeli qalanlar

- Private key-in tehlukesiz fayl yolu qeyd olunmayib.
- Private key passphrase olub-olmadigi qeyd olunmayib.
- Domain istifade olunacaqmi, yoxsa yalniz `http://169.58.172.211` qalacaqmi, qerar deqiq deyil.
- HTTPS/SSL indi teleb olunur, yoxsa sonraki merhelede, qerar deqiq deyil.
- Kohne frontend-in port `80`-den cixarilmasi tetbiq edildi: port `80` indi Caddy vasitesile `30081` yeni UI-a yonlenir.

## Sonraki ehtiyat addimlar

1. Domain elave olunacaqsa, `/opt/turn/turn-api/ops/stack.env` icinde `TURN_DOMAIN` ve `PUBLIC_BASE_URL` deyerlerini yoxlamaq/deyismek.
2. HTTPS/SSL lazim olsa, Caddy port `443` mapping ve TLS ayarlari ayrica edilmelidir.
3. Deyisikliklerden sonra tekrar yoxlamaq:
   - `/`
   - `/login`
   - `/register`
   - `/api/auth/csrf`
   - login/register API flow

## Qisa netice

Servere SSH ile giris var: `root@169.58.172.211:22`.

Caddy Docker Compose daxilinde isleyir ve config yolu tapilib:

`/opt/turn/turn-api/ops/Caddyfile`

Hazirda IP uzerinden yeni frontend ve `/api/auth/csrf` isleyir. Qalan esas qerar domain/HTTPS-dir.
