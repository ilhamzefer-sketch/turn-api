# Production checklist

## Buraxılışdan əvvəl

- [ ] Real domen və etibarlı TLS sertifikatı aktivdir.
- [ ] Kapital Bank production merchant məlumatları bankdan alınıb.
- [ ] Stage-də success, decline, cancel, timeout və yanlış məbləğ ssenariləri keçib.
- [ ] PostgreSQL və Redis TLS ilə işləyir və public internetə açıq deyil.
- [ ] `ADMIN_USERNAME`, `ADMIN_PASSWORD_HASH` və `APP_JWT_SECRET` secret manager-dədir.
- [ ] JWT issuer/audience və access token müddətləri stage və production üçün təsdiqlənib.
- [ ] User, privileged və admin inactivity/absolute sessiya limitləri biznes əməliyyatlarına uyğundur.
- [ ] Logout, inactivity, absolute timeout, credential change və refresh-token reuse smoke-testləri keçib.
- [ ] Auth cavablarında `Cache-Control: no-store`, refresh cookie-də `Secure`, `HttpOnly` və `SameSite` aktivdir.
- [ ] Sessiya audit hadisələri monitorinq olunur və 90 günlük retention job işləyir.
- [ ] GitHub `stage` və `prod` environments üçün approval protection aktivdir.
- [ ] Branch protection test, dependency review və CodeQL check-lərini məcburi edir.
- [ ] Gündəlik backup schedule qurulub və ayrıca storage-a yazılır.
- [ ] Restore sınağı boş database üzərində uğurla keçirilib.
- [ ] Prometheus alertləri notification kanalına qoşulub.
- [ ] Xarici penetration test və bankın qəbul testi tamamlanıb.

## Buraxılış

- [ ] Database backup alınıb.
- [ ] Flyway `validate` uğurludur.
- [ ] Image SHA ilə deploy edilir, yalnız `stage`/`prod` floating tag-a güvənilmir.
- [ ] Readiness yaşıl olduqdan sonra trafik açılır.
- [ ] Payment success və decline smoke-test edilir.
- [ ] Flyway V24 sessiya lifecycle migration-u tətbiq olunub və aktiv sessiyalar görünür.

## Rollback

- [ ] Əvvəlki image SHA məlumdur.
- [ ] Geri dönüş database migration ilə uyğunluğu pozmur.
- [ ] Data restore yalnız təsdiqlənmiş incident proseduru ilə edilir.
