# Coin sisteminin yayımlama yoxlama siyahısı

Bu sənəd V29–V32 miqrasiyalarını və onlarla birlikdə yayımlanan coin balansı, coin abunəliyi, biznes otaq limiti və çox-adminli idarəetmə funksiyalarını əhatə edir.

## Yayımlamadan əvvəl

- PostgreSQL bazasının bərpa oluna bilən backup-ını yaradın və bərpa yoxlamasının nəticəsini qeyd edin.
- Mövcud istifadəçi, aktiv abunəlik, biznes otağı və tamamlanmış subscription ödənişi saylarını qeyd edin.
- Stage mühitində `APP_LEGACY_API_ENABLED=false` və `APP_PAYMENT_RECONCILIATION_ENABLED=false` olduğunu təsdiqləyin.
- `APP_WALLET_COINS_PER_AZN=10` və düzgün WhatsApp ünvanını təsdiqləyin.
- Production admin istifadəçi adı və BCrypt şifrəsinin lokal default dəyərlərdən fərqli olduğunu təsdiqləyin.
- Backend və frontend-in tam test, lint və build yoxlamalarını uğurla tamamlayın.
- PostgreSQL migration testlərini Docker/Testcontainers işləyən CI və ya stage mühitində icra edin.

## Yayımlama ardıcıllığı

1. Yeni backend image-ni yayımlayın. Flyway V29–V32 miqrasiyalarını tətbiq edəcək.
2. Readiness uğurlu olmadan frontend-i yeni versiyaya keçirməyin.
3. Backend hazır olduqdan sonra frontend image-ni yayımlayın.
4. Wallet, subscription, biznes otaqları və admin panel üçün aşağıdakı smoke testləri aparın.

## Məlumat bütövlüyü

- Hər istifadəçinin bir və yalnız bir wallet hesabı var.
- Yeni wallet-lər `0 coin` ilə başlayır və heç bir balans mənfi deyil.
- Wallet ledger balansı wallet hesabının cari balansı ilə uyğun gəlir.
- Aktiv köhnə abunəliklərin statusu, başlanğıc və bitmə tarixləri qorunub.
- Beşdən çox mövcud otağı olan bizneslərin yeni otaq limiti cari aktiv otaq sayından az deyil.
- Tamamlanmış köhnə subscription qəbzləri tarixçədə qalır.
- Yarımçıq köhnə subscription bank sessiyaları `CANCELLED` vəziyyətindədir.
- Aktiv planlar yalnız `INDIVIDUAL_MONTHLY` və `BUSINESS_MONTHLY` coin planlarıdır.

## Smoke testləri

- Yeni və mövcud istifadəçi wallet səhifəsini aça bilir.
- `100 coin` daxil ediləndə `10 AZN` görünür.
- Bank kartı boz və deaktivdir, statusu mətnlə izah olunur.
- WhatsApp düyməsi seçilmiş coin və AZN məbləğini ötürür.
- Fərdi abunəlik `30 coin`, biznes abunəliyi `100 coin` çıxır.
- Kifayət qədər coin olmadıqda balans dəyişmir və balans artırma keçidi görünür.
- Eyni ödəniş istinadı təkrar göndərildikdə ikinci debit yaranmır.
- Biznesin 6-cı otağı bloklanır və WhatsApp müraciəti görünür.
- Admin istifadəçini tapa, auditli coin əlavə edə və biznes limitini yalnız artıra bilir.
- Yeni admin yaradıla və həmin hesabla giriş edilə bilir.
- Köhnə subscription bank endpoint-ləri autentifikasiyadan sonra `410 Gone` qaytarır.

## Monitorinq

- Readiness: `/actuator/health/readiness`
- Liveness: `/actuator/health/liveness`
- Migration və constraint xətaları üçün backend loglarını izləyin.
- Gözlənilməz wallet debit, `PAYMENT_REQUIRED`, idempotency conflict və otaq-limit xətalarını izləyin.
- İlk gün istifadəçi, wallet, completed coin payment və aktiv subscription saylarını əvvəlki göstəricilərlə müqayisə edin.

## Geri dönüş qaydası

- Miqrasiyaları əl ilə geri çevirməyin və köhnə subscription bank yolunu yenidən aktiv etməyin.
- Problem frontend-dədirsə əvvəlki frontend image-nə qayıdın; yeni backend coin contract-ını saxlayın.
- Problem backend-dədirsə verilənlər bazasını saxlayan uyğun düzəldilmiş backend image yayımlayın.
- Backup-dan tam bərpa yalnız planlı dayanma zamanı və backup-dan sonra yaranmış wallet əməliyyatlarının ayrıca uzlaşdırılması ilə aparılmalıdır.
