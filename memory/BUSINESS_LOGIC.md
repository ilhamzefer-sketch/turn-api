# E-Növbə biznes məntiqi

## 1. Məhsulun məqsədi

E-Növbə fiziki növbəni rəqəmsallaşdırır. Növbə yaradan tərəf xidmət nöqtəsini və növbəni idarə edir. Müştəri QR kodu oxudaraq, UID kodunu yazaraq və ya kabinetdən uzaqdan qoşularaq nömrə götürür, qarşısında neçə nəfər olduğunu və təxmini gözləmə vaxtını görür.

## 2. Rollar

### Vahid istifadəçi (`USER`)

- Yeni əsas hesab modelidir və Azərbaycan telefon nömrəsi ilə tanınır.
- İstifadəçi telefon nömrəsini yalnız `0501234567` formasında, `0` ilə başlayan dəqiq 10 rəqəm kimi daxil edir; `+994`, boşluq, tire və fərqli uzunluq qəbul edilmir. Daxildə nömrə `+994501234567` identifikatoruna normallaşdırılır.
- Eyni normallaşdırılmış telefon yalnız bir `USER` hesabına aid ola bilər.
- Hesab pulsuz yaradılır və gələcəkdə eyni profil müştəri, individual specialist, business sahibi/admini və room owner kontekstlərində istifadə ediləcək.
- Köhnə `REGISTRATION`, `CUSTOMER` və `QUEUE_MANAGER` modeli yalnız geri dönüş ehtiyacı üçün kodda saxlanılır; legacy API production default olaraq bağlıdır.

### Növbə yaradan (`REGISTRATION`)

- `FƏRDİ` və ya `KORPORATİV` hesab kimi qeydiyyatdan keçir.
- Ödəniş tamamlandıqdan sonra hesab `ACTIVE` olur.
- Öz növbələrini yaradır, izləyir, növbəti müştəriyə keçir və sıfırlayır.
- Başqa hesabın növbəsini idarə edə bilməz.

### Müştəri (`CUSTOMER`)

- Ödənişsiz qeydiyyatdan keçir və daxil olur.
- QR/UID ilə və ya açıq növbələr siyahısından uzaqdan növbə götürür.
- İştirak etdiyi növbələrin tarixçəsini görür.
- Tarixçədə növbəyə öz görünən adını verə və xidmətə 1-5 arası qiymət yaza bilər.

### Qeydiyyatsız müştəri (`GUEST`)

- Hesab yaratmadan yalnız QR/UID vasitəsilə növbəyə qoşulur.
- Ad və soyad yazması məcburidir.
- Yeni axında telefon nömrəsi saxlanır; köhnə UI uyğunluğu üçün telefon sahəsi keçid müddətində nullable-dır.
- Telefon aktiv `USER` hesabına aiddirsə giriş dərhal həmin hesaba bağlanır.
- Şəxs sonradan eyni telefonla qeydiyyatdan keçərsə əvvəlki uyğun guest tarixçəsi hesaba bağlanır.

### Növbə idarəçisi (`QUEUE_MANAGER`)

- Yalnız korporativ hesabın konkret bir növbəsinə bağlıdır.
- Öz istifadəçi adı və BCrypt ilə saxlanan şifrəsi var.
- Yalnız təyin edildiyi növbəni görə, növbəti müştəriyə keçirə və sıfırlaya bilər.

### Admin (`ADMIN`)

- Bütün növbə yaradan hesabların siyahısını və qeydiyyat tarixini görür.
- Fərdi/korporativ, ödəniş statusu, ay və mətn üzrə filtr edə bilir.
- Növbə yaradanların, müştərilərin, aktiv növbələrin, expired hesabların və pending ödənişlərin sayını görür.
- Ümumi gəlir və uğurlu ödəniş sayını faktiki tamamlanmış payment session-lara əsasən görür.
- Son 50 bank əməliyyatının statusunu, məbləğini, order ID-sini və tarixini izləyir.
- Növbələrin cari xidmət nömrəsini, gözləyən sayını, orta xidmət vaxtını və aktivlik vəziyyətini monitorinq edir.

### Biznes üzvləri

- Hər biznesin bir `PRIMARY_OWNER` üzvü olur və həmin şəxs birdən çox biznes yarada bilər.
- `ADMIN` biznes profilini, filialları, otaqları, üzvləri və otaq təyinatlarını əsas sahibə ehtiyac olmadan idarə edir.
- `EMPLOYEE` biznes səviyyəsində idarəetmə icazəsi almır; yalnız qəbul etdiyi otaq təyinatlarını idarə edir.
- Yeni üzvlük və otaq təyinatı əvvəlcə `PENDING_ACCEPTANCE` olur və istifadəçi tərəfindən ayrıca qəbul və ya rədd edilir.
- Biznesdən çıxarılan əməkdaşın aktiv otaq təyinatları ləğv olunur, tarixi qeydlər silinmir.

## 3. Legacy növbə yaradanın qeydiyyat və ödəniş axını

Bu axın artıq əsas məhsul axını deyil və `app.legacy-api.enabled=false` olduqda `410 Gone` qaytarır.

1. İstifadəçi ad, soyad, email, şifrə və hesab növünü daxil edir.
2. Sistem qeydiyyatı `PENDING_PAYMENT` statusunda yaradır.
3. Fərdi hesab üçün məbləğ `20 AZN`, korporativ hesab üçün `100 AZN` hesablanır.
4. Backend Birbank-da sifariş yaradır və bank checkout URL-ni qaytarır.
5. İstifadəçi kart məlumatını yalnız bank səhifəsində daxil edir. E-Növbə kartın tam nömrəsini və CVV-ni qəbul etmir və saxlamır.
6. Bank statusu yalnız backend tərəfindən yoxlanılır. Frontend callback parametrinə etibar edilmir.
7. `FullyPaid/Paid` və məbləğ, valyuta, order uyğun olduqda ödəniş `COMPLETED`, profil `ACTIVE` olur.
8. `Cancelled/Canceled/Declined/Rejected/Expired` nəticəsi `CANCELLED` sayılır və profil aktivləşmir.
9. Gözləyən ödənişlər background reconciliation ilə periodik yenidən yoxlanılır.
10. Uğurlu ödənişdən sonra avtomatik login yalnız bir dəfə verilə bilər; sonrakı giriş email və şifrə ilə edilir.

Ödəniş sessiyası tokeni URL-də və JSON-da göstərilmir. Browser onu `HttpOnly` cookie ilə göndərir, DB-də isə yalnız SHA-256 hash saxlanılır.

## 3.1. Yeni vahid hesab qeydiyyatı və girişi

1. `POST /api/auth/register` ad, soyad, telefon və şifrə qəbul edir.
2. Telefon inputu yalnız `0XXXXXXXXX` formasında qəbul edilir, daxildə Azərbaycan `+994` nömrəsi kimi normallaşdırılır və unikal saxlanır.
3. Telefon mövcud deyilsə pulsuz `ACTIVE` hesab yaranır.
4. Telefon `PENDING` hesabına aiddirsə eyni hesab aktivləşdirilir; yeni hesab yaradılmır və dəvətdə yazılmış ilkin adlar audit məlumatı kimi qorunur.
5. Telefon `PASSWORD_RESET_REQUIRED` hesabına aiddirsə eyni hesaba yeni şifrə qoyulur və əvvəlki bütün sessiyalar ləğv edilir.
6. Aktiv hesab üçün təkrar qeydiyyat rədd edilir.
7. `POST /api/auth/login` telefon və şifrə ilə vahid girişdir.
8. Beş ardıcıl səhv şifrə hesabı telefon səviyyəsində 15 dəqiqə kilidləyir.
9. `USER` öz aktiv refresh sessiyalarını görə, konkret sessiyanı, digər sessiyaları və ya bütün sessiyaları ləğv edə bilər.
10. Köhnə email əsaslı endpoint-lər frontend migrasiyası tamamlanana qədər compatibility üçün saxlanılır.

Sessiya müddətləri rol üzrə ayrıca idarə olunur: adi istifadəçi üçün 30 dəqiqə fəaliyyətsizlik və 12 saat mütləq müddət, səlahiyyətli biznes istifadəçisi üçün 15 dəqiqə və 8 saat, platform administratoru üçün 10 dəqiqə və 4 saat. Bütün dəyərlər environment konfiqurasiyası ilə dəyişdirilə bilər.

Yeni şifrələr 8-128 simvol qəbul edir, geniş istifadə olunan zəif şifrələr rədd olunur və SHA-256 pre-hash üzərindən BCrypt ilə saxlanılır.

## 3.2. Provider abunəliyi və coin ödənişi

1. Hər `IndividualWorkspace` və hər `Business` ayrıca abunəliyə, limitlərə, bitmə tarixinə və ödəniş tarixçəsinə malikdir.
2. Aktiv paketlər aylıqdır: fərdi workspace üçün `INDIVIDUAL_MONTHLY`, biznes üçün `BUSINESS_MONTHLY`.
3. Şəxsi workspace sahibi, biznes `PRIMARY_OWNER` və `ADMIN` paketləri görə, coin balansı ilə ödəniş edə və qəbz tarixçəsini görə bilər.
4. Tamamlanmış coin ödənişi abunəliyi `ACTIVE` edir və mövcud aktiv müddət varsa yeni ay onun sonuna əlavə olunur.
5. Abunəlik bitdikdən sonra yeddi günlük `GRACE_PERIOD`, sonra `SUSPENDED` statusu tətbiq olunur.
6. Aktiv və ya grace-period abunəliyi olmadan otaq publish edilmir, canlı sessiya açılmır, canlı növbəyə yeni qoşulma və yeni planlı booking qəbul edilmir.
7. Suspension mövcud booking-ləri, otaqları və tarixçəni silmir. Səlahiyyətli operator əvvəlcədən yaranmış booking-i complete, cancel və reschedule edə bilər.
8. Paket otaq və əməkdaş sayını limitləyir; canlı iştirakçı və booking sayı limitlənmir. Limit aşımı məlumat silmir, yalnız yeni əməliyyatları dayandırır.
9. Tamamlanmış köhnə bank qəbzləri yalnız tarixçə üçün saxlanılır; yeni bank subscription sessiyası yaradıla və təsdiqlənə bilməz.

## 3.3. Coin wallet təməli

1. Hər vahid `USER` hesabının yalnız bir coin wallet-i var. Mövcud istifadəçilər üçün wallet miqrasiya zamanı, yeni aktiv və ya pending istifadəçilər üçün hesab yaranan tranzaksiyada yaradılır.
2. Coin yalnız müsbət tam ədəd kimi saxlanılır və wallet balansı mənfi ola bilməz.
3. Hər balans dəyişikliyi ayrıca append-only ledger sətri yaradır; sətirdə əməliyyat növü, istiqamət, məbləğ, əvvəlki və sonrakı balans, icraçı, unikal istinad, optional izah və tarix saxlanılır.
4. Eyni wallet və istinadla eyni əməliyyat təkrar göndərilərsə əvvəlki nəticə qaytarılır və balans ikinci dəfə dəyişmir. Eyni istinad fərqli əməliyyat üçün istifadə edilə bilməz.
5. Balans dəyişikliyi wallet sətrinə pessimistic lock tətbiq edən bir database tranzaksiyasında aparılır.
6. `ADMIN_CREDIT` əməliyyatı icraçı admin istinadını və səbəbi məcburi saxlayır.
7. Provider abunəliyi yalnız coin wallet-dən debit olunur; bank top-up axını hələ aktiv deyil.
8. Coin qiyməti konfiqurasiya olunur və ilkin qayda `10 coin = 1 AZN`-dir. Balans artırma ekranı bu qaydanı backend-dən alır.
9. Bank kartı seçimi ilkin mərhələdə deaktivdir. WhatsApp müraciəti `https://wa.me/message/P63GI5XJ3PQLC1` ünvanına yönləndirilir və avtomatik coin əlavə etmir.

## 4. Hesab statusları

Vahid `USER` statusları:

- `PENDING` - biznes tərəfindən hazırlanmış, şifrəsiz hesabdır və login edə bilməz.
- `ACTIVE` - qeydiyyatı tamamlanmış hesabdır.
- `PASSWORD_RESET_REQUIRED` - admin resetindən sonra şifrəsi olmayan hesabdır; adi qeydiyyat forması ilə yeni şifrə qoyur.
- `SUSPENDED` - girişə icazə verilməyən hesabdır.
- `ANONYMIZED` - şəxsi məlumatları silinmiş hesabdır.

Köhnə ödənişli `REGISTRATION` statusları keçid dövründə aşağıdakı kimi qalır:

- `PENDING_PAYMENT` - ödəniş yaradılıb, amma təsdiqlənməyib.
- `ACTIVE` - ödəniş təsdiqlənib və növbə yaratmağa icazə var.
- `EXPIRED` - abunəlik/ödəniş etibarlı deyil; hesaba bağlı bütün növbələr deaktiv edilir.

`ACTIVE` olmayan növbə yaradan sistemə daxil ola və aktiv növbə idarə edə bilməz.

## 5. Provider və növbə yaratma qaydaları

## 5.1. Yeni provider strukturu

1. Hər aktiv `USER` pulsuz şəxsi müştəri kontekstinə malikdir.
2. İstifadəçi maksimum bir `IndividualWorkspace` yarada və onun daxilində maksimum bir şəxsi otaq saxlaya bilər.
3. İstifadəçi birdən çox biznes yarada, bizneslərdə owner/admin ola və başqa bizneslərin otaqlarına sahib təyin edilə bilər.
4. Biznes yarananda yaradan istifadəçi avtomatik aktiv `PRIMARY_OWNER` üzvlüyü alır.
5. Biznes otaq yaratmazdan əvvəl filial yaratmalıdır. Biznes otağının ünvanı filiala aiddir.
6. Şəxsi otaq filialsız ola bilər və şəxsi public ünvanı optional saxlayır.
7. Yeni otaq `DRAFT` statusunda yaranır, `LIVE_QUEUE` və ya `PLANNED_BOOKING` rejimlərindən yalnız birini seçir.
8. Otaq `PUBLIC`, `UNLISTED` və ya `PRIVATE` görünürlüyə malikdir.
9. Biznes otağına bir neçə `ROOM_OWNER` təyin edilə bilər və eyni istifadəçi bir neçə otağa sahib ola bilər.
10. Otaq dəvəti qəbul ediləndə həmin biznes üzrə gözləyən üzvlük varsa eyni tranzaksiyada avtomatik qəbul olunur və otaq təyinatı aktivləşir.
11. Hər otaq sahibi öz telefonunun həmin otağın public səhifəsində göstərilib-göstərilməməsini özü seçir.
12. Workspace switcher müştəri, individual workspace, idarə edilən biznes və qəbul edilmiş otaq kontekstlərini qaytarır.
13. Filial aktiv otaqları olduğu müddətdə arxivləşdirilə bilməz. Otaq silinməsi fiziki delete deyil, `ARCHIVED` statusudur.

Pending hesab yaradılması üçün başlanğıc limitlər biznesə gündə `500`, dəvət edən idarəçiyə gündə `100` və dəqiqədə `20`-dir. Dəyərlər environment konfiqurasiyası ilə dəyişdirilə bilər.

## 5.2. Otaq cədvəli və konfiqurasiyası

1. Hər otaq bir həftə günü üçün bir neçə iş intervalı saxlaya bilər. Aktiv intervallar üst-üstə düşə bilməz.
2. Bir günün cədvəli eyni əməliyyatla seçilmiş digər günlərə kopyalana bilər. Hədəf günlərin əvvəlki intervalları əvəz edilir.
3. Tarix istisnaları `CLOSED`, `CUSTOM_HOURS` və `BLOCKED_INTERVAL` növlərində saxlanılır.
4. `CLOSED` olan tarixdə başqa istisna saxlanmır. Xüsusi və bloklanan eyni növlü intervallar üst-üstə düşmür.
5. Otağın standart görüş müddəti 1-1440 dəqiqədir və bütün xidmətlər üçün eynidir.
6. Görüşdən sonrakı buffer 0-1440 dəqiqə, rezervasiya pəncərəsi 1-90 gün, minimum advance 0-10080 dəqiqədir.
7. Xidmət siyahısı optional-dır. Xidmətin adı, açıqlaması, aktivliyi və optional sabit AZN qiyməti olur; xidmət ayrıca görüş müddəti müəyyən etmir.
8. Canlı növbə reseti `DAILY_AT_TIME` və ya `EVERY_INTERVAL` qaydası ilə konfiqurasiya edilir. Günlük qayda yalnız yerli saat, interval qaydası yalnız müsbət dəqiqə intervalı qəbul edir.
9. Canlı otaqda iştirakçı limiti optional-dır və yeni iştirakçı qəbulunun açıq/bağlı konfiqurasiyası saxlanılır.
10. Yayımlanma üçün otağın əsas məlumatları, aktiv owner assignment-i və ən azı bir aktiv həftəlik intervalı olmalıdır.
11. `LIVE_QUEUE` otağı əlavə olaraq reset qaydısı, `PLANNED_BOOKING` otağı rezervasiya pəncərəsi və ləğv parametrləri tələb edir.
12. Otaq yalnız səlahiyyətli business owner/admin, aktiv room owner və ya individual workspace sahibi tərəfindən konfiqurasiya edilə bilər.
13. Yayımlanmış otağın konfiqurasiyası onu etibarsız vəziyyətə sala bilməz. Otaq ayrıca `INACTIVE` edilə və sonradan şərtlər tamam olduqda yenidən yayımlana bilər.

## 5.3. Otaq əsaslı canlı növbə

1. Yayımlanmış `LIVE_QUEUE` otağı üçün eyni anda yalnız bir açıq `LiveQueueSession` ola bilər və sessiya otaq yayımlananda avtomatik `AUTO` rejimində yaradılır.
2. Sessiya gündəlik yerli saatda və ya konfiqurasiya edilmiş intervaldan sonra reset olunur. Reset köhnə sessiyanı `CLOSED`, bütün aktiv iştirakçıları `RESET` edir və yeni boş sessiya yaradır.
3. Yeni iştirakçı yalnız sessiya açıq, otaq qəbul vəziyyətində və iştirakçı limiti dolmamış olduqda qoşula bilər.
4. `AUTO` qəbul rejimi otağın timezone-u, həftəlik cədvəli və tarix istisnalarına baxaraq uyğun saatda qəbulu avtomatik açıb-bağlayır. İlkin manual aktivləşdirmə tələb olunmur. Otaq sahibi qəbul vəziyyətini `FORCE_OPEN` və ya `FORCE_CLOSED` ilə müvəqqəti override edə, sonra yenidən avtomatik rejimə qaytara bilər.
5. Guest istifadəçi public link və ya QR ilə yalnız ad və Azərbaycan telefon nömrəsi daxil edir. Şifrə və hesab məcburi deyil.
6. Eyni normallaşdırılmış telefon eyni sessiyada ikinci aktiv giriş yarada bilməz; təkrar sorğu mövcud girişin public reference-ını qaytarır.
7. Otaq sahibi telefon, walk-in və digər offline mənbə ilə manual guest əlavə edə bilər. Ad, telefon və daxili qeyd yalnız səlahiyyətli operator cavabında görünür.
8. Aktiv iştirakçı statusları `WAITING`, `CURRENT` və `SKIPPED`, terminal statuslar `COMPLETED`, `REMOVED` və `RESET`-dir.
9. `call-next` ilk gözləyəni cari edir. Cari iştirakçını tamamlamaq avtomatik növbəti gözləyəni cari edir.
10. Otaq sahibi iştirakçını skip, restore, send-to-end və remove edə bilər. Arbitrary sıra dəyişmə yoxdur və bütün terminal qeydlər tarixçədə saxlanılır.
11. Nömrə verilməsi sessiya səviyyəsində pessimistic DB lock ilə seriallaşdırılır. Sessiya daxilində mövqe, aktiv telefon və cari iştirakçı ayrıca unique constraint-lərlə qorunur.
12. Otağın standart müddəti public təxmini gözləmə vaxtının hesablanmasında istifadə olunur.
13. Public cavab yalnız anonim `publicReference`, mövqe və status göstərir; guest adı, telefonu və daxili qeydi göstərmir.
14. Bir otaq üçün istənilən sayda daimi QR credential yaradıla bilər. Token yalnız yaradılarkən qaytarılır, bazada SHA-256 hash saxlanılır və hər credential ayrıca regenerate və revoke edilə bilər.
15. Guest contact eyni telefonla sonradan qeydiyyatdan keçən `USER` hesabına bağlanır və otaq canlı növbə tarixçəsi `/api/users/me/live-queue-history` vasitəsilə görünür.
16. Açıq sessiya bağlanmadan otaq `PLANNED_BOOKING` rejiminə keçirilə və ya arxivləşdirilə bilməz.

## 5.4. Planlı rezervasiya

1. Yalnız `PUBLISHED` və `PLANNED_BOOKING` rejimli otaq rezervasiya qəbul edir. `PUBLIC` və `UNLISTED` otaqların boş saatları public görünür, `PRIVATE` otaq public booking axınına daxil edilmir.
2. Boş saatlar otağın timezone-u, həftəlik intervalları, tarix istisnaları, standart müddəti və görüşdən sonrakı buffer əsasında yaradılır.
3. Müştəri üçün tarix otağın booking window-u daxilində, başlanğıc isə minimum advance müddətindən sonra olmalıdır. Otaq operatoru manual booking yaradarkən minimum advance qaydasını keçə bilər, amma keçmiş saata booking yarada bilməz.
4. Aktiv booking-lər `ACTIVE`, xidmət tamamlandıqda `COMPLETED`, ləğv və no-show zamanı `CANCELLED` olur. No-show ayrıca status deyil, `NO_SHOW` cancellation reason-dır.
5. Qeydiyyatlı `USER` boş saatı seçdikdə booking ayrıca owner təsdiqi olmadan dərhal aktiv olur. Eyni istifadəçinin eyni otaqda ikinci aktiv booking-i ola bilməz.
6. Otaq sahibi telefon, walk-in və digər offline mənbə ilə ad və telefon əsasında manual guest booking yarada bilər. Guest sonradan eyni telefonla qeydiyyatdan keçəndə booking tarixçəsi hesabına bağlanır.
7. Müştəri cancellation cutoff keçməyibsə öz booking-ini cancel və ya yalnız başqa boş saata reschedule edə bilər. Operator cutoff-dan sonra da dəyişiklik edə bilər.
8. Operator cancel üçün səbəb və iştirakçının məlumatlandırıldığını təsdiqləməlidir. Operator reschedule zamanı da iştirakçı ilə əlaqə təsdiqi verir.
9. Otaq, start, end, buffer və iştirakçı məlumatı client-dən etibarlı sayılmır. End və blocking end serverdə otaq konfiqurasiyasından hesablanır.
10. Booking yaradılması və dəyişdirilməsi otaq səviyyəsində pessimistic DB lock ilə seriallaşdırılır. Aktiv start və aktiv customer qaydaları ayrıca unique constraint-lərlə qorunur.
11. Hər booking qısa, unikal `B-...` reference alır. Create, reschedule, cancel və complete əməliyyatları actor, əvvəlki/yeni saat, səbəb və məlumatlandırma təsdiqi ilə audit cədvəlində saxlanılır.
12. Müştəri öz booking tarixçəsində owner-in daxili qeydini görmür. Otaq owner/admin yalnız idarə etdiyi otaqların iştirakçı adı, telefonu və daxili qeydlərini görə bilər.
13. Gələcək aktiv booking-lər tamamlanmadan, ləğv edilmədən və ya köçürülmədən otaq `LIVE_QUEUE` rejiminə keçirilə bilməz.

## 5.5. Legacy növbə qaydaları

Növbə üçün aşağıdakılar tələb olunur:

- Ünvan
- Xidmətin/işin adı
- Ən azı bir kateqoriya
- Sıfırlanma rejimi
- Korporativ növbədirsə idarəçi istifadəçi adı və şifrəsi

Kateqoriyalar təkrarsız saxlanılır. Hazırkı texniki limit bir növbə üçün maksimum `50` kateqoriya, hər kateqoriya üçün maksimum `100` simvoldur.

### Fərdi hesab

- Bütün hesab ömrü ərzində maksimum bir növbə yarada bilər.

### Korporativ hesab

- İstədiyi qədər növbə yarada bilər.
- Hesab sahibi bütün növbələrini idarə edir.
- Hər növbə üçün ayrıca və unikal idarəçi hesabı yaradılır.
- Növbə idarəçisi yalnız öz növbəsini idarə edir.

Növbə yarananda unikal UUID tipli QR/UID token yaradılır. QR çapında həmin UID mətn kimi QR-ın altında göstərilməlidir ki, kamera işləmədikdə əl ilə daxil edilə bilsin.

## 6. Növbəyə qoşulma

### QR/UID ilə

- Qeydiyyatlı müştərinin adı hesabdan götürülür və tarixçə yaradılır.
- Qeydiyyatsız müştəri ad və soyad daxil etməlidir.
- Token mövcud və növbə aktiv olmalıdır.

### Evdən qoşulma

- Daxil olmuş `CUSTOMER` və ya yeni vahid `USER` açıq növbələr siyahısından queue ID və ya QR token ilə qoşula bilər.
- Müştəri növbəni öz kabinetində istədiyi görünən adla saxlaya bilər.

### Nömrənin verilməsi

1. `lastIssuedNumber` bir vahid artırılır.
2. Artırılmış dəyər müştərinin növbə nömrəsi olur.
3. Eyni nömrə həmin növbədə ikinci dəfə verilə bilməz.
4. Deaktiv növbəyə yeni iştirakçı qəbul edilmir.

## 7. Gözləmə və vaxt hesablaması

Əsas sahələr:

- `currentServingNumber` - hazırda xidmət göstərilən nömrə.
- `lastIssuedNumber` - son verilmiş nömrə və ümumi verilmiş nömrələrin sayğacı.
- `averageServiceMinutes` - bir müştəriyə orta xidmət müddəti.

Yeni nömrə alan konkret müştəri üçün:

```text
qarşıdakı şəxslər = max(0, müştəri nömrəsi - currentServingNumber - 1)
təxmini gözləmə = qarşıdakı şəxslər * averageServiceMinutes
```

Növbə idarəetmə ekranında ümumi gözləyənlər üçün:

```text
gözləyənlərin sayı = max(0, lastIssuedNumber - currentServingNumber)
ümumi təxmini vaxt = gözləyənlərin sayı * averageServiceMinutes
```

İlk başlanğıc orta xidmət müddəti `5 dəqiqə`dir. Hər `Növbəti nömrəyə keç` əməliyyatında əvvəlki xidmətin faktiki müddəti hesablanır:

```text
orta xidmət müddəti = totalServiceMinutes / servedCustomersCount
```

Hesablamada nəticə minimum `1 dəqiqə` qəbul edilir.

## 8. Növbənin idarə olunması

### Növbəti nömrəyə keç

- Əməliyyatı yalnız növbə sahibi və ya həmin növbənin idarəçisi edə bilər.
- Gözləyən yoxdursa əməliyyat rədd edilir.
- Cari xidmət nömrəsi bir vahid artırılır.
- Əvvəlki xidmət intervalı orta xidmət vaxtına əlavə olunur.

### Manual sıfırla

- Cari nömrə, son verilmiş nömrə, xidmət statistikası və son keçid vaxtı sıfırlanır.
- Orta xidmət müddəti yenidən `5 dəqiqə` olur.
- `MANUAL` rejimli deaktiv növbə manual sıfırlananda yenidən aktiv edilir.

## 9. Avtomatik sıfırlanma rejimləri

- `DAILY` - default davranışdır; növbə növbəti gün saat `00:00`-da sıfırlanır və deaktiv olur.
- `CUSTOM_DATE` - seçilən gün bitdikdən sonra, növbəti gün `00:00`-da sıfırlanır və deaktiv olur.
- `MANUAL` - avtomatik tarix yoxdur; sahibi və ya idarəçi istədikdə sıfırlayır.

Avtomatik vaxt çatdıqda `currentServingNumber` və `lastIssuedNumber` sıfırlanır, növbə `active=false` olur. Keçmiş müştəri tarixçəsi silinmir.

## 10. Müştəri tarixçəsi və qiymətləndirmə

Qeydiyyatlı müştərinin hər qoşulması tarixçədə saxlanılır:

- Növbə və xidmət adı
- Ünvan və kateqoriyalar
- Aldığı nömrə
- Qoşulma tarixi
- Müştərinin verdiyi xüsusi görünən ad
- 1-5 qiymət və qeyd

Müştəri yalnız öz tarixçə qeydini dəyişə və qiymətləndirə bilər.

Yeni `USER` tarixçəsi həm hesabla birbaşa yaradılmış queue entry-ləri, həm də eyni normallaşdırılmış telefonla bağlanmış guest entry-ləri bir siyahıda göstərir. Mənbə `REGISTERED` və ya `GUEST` kimi ayrılır. Guest qeydin ilkin şəxsi və audit məlumatları yenidən yazılmır.

## 11. Təhlükəsizlik invariatları

- Köhnə şifrələr BCrypt, yeni `USER` şifrələri SHA-256 pre-hash üzərindən BCrypt kimi saxlanılır.
- Access token qısaömürlü JWT-dir; refresh token `HttpOnly` cookie-də, DB-də hash kimi saxlanılır.
- Dəyişiklik edən browser sorğuları CSRF token tələb edir.
- Bank kartı ilə balans artırma aktiv deyil və cari production prosesində bank API çağırışı edilmir.
- Rate limit auth, payment və public queue endpoint-lərinə tətbiq olunur; stage/prod mühitində Redis istifadə edilir.
- PostgreSQL və Redis portları internetə açılmamalıdır.
- Növbə sahibi, müştəri və queue manager ID-ləri request body-dən etibarlı sayılmır; JWT istifadəçisi ilə əvəz edilir.

## 12. Step 6: subscription, support və reporting

1. Provider abunəliyi yalnız aylıq coin paketi ilə aktivləşdirilir: fərdi iş sahəsi `30 coin` (`3 AZN` ekvivalenti), biznes isə `100 coin` (`10 AZN` ekvivalenti) ödəyir. Bank payment provider-ləri subscription checkout üçün istifadə edilmir.
2. Coin çıxışı, dəyişməz subscription payment qeydi və abunəliyin aktivləşdirilməsi/uzadılması bir DB transaction-da tamamlanır. Eyni user və idempotency key təkrar göndəriləndə ikinci debit və ikinci uzatma yaranmır.
3. Fərdi planın otaq limiti `1`, biznes planının standart otaq limiti `5`-dir. Altıncı aktiv biznes otağı yaradılmır və daha yüksək limit üçün support müraciəti göstərilir.
4. Otaq əməliyyatları abunəlik gate-i ilə qorunur. Test mühitində əvvəlki mərhələlərin regression testləri üçün gate ayrıca söndürülə bilər; production default aktivdir.
5. Hesab sahibliyi mübahisəsi public support müraciəti ilə yaradılır və admin `NO_ACTION`, `SUSPEND`, `RESET_PASSWORD` və ya `RESTORE_ACCESS` qərarı verə bilər.
6. Admin password reset etdikdə bütün sessiyalar ləğv edilir, şifrə silinir və hesab müddətsiz `PASSWORD_RESET_REQUIRED` olur. İstifadəçi eyni telefonla adi register ekranında yeni şifrə qoyur.
7. Telefon dəyişməsi və hesab silinməsi yalnız support müraciəti və admin qərarı ilə aparılır. Aktiv biznes primary owner-i ownership-i ötürmədən anonymize edilmir.
8. Biznes primary ownership transferi hədəf aktiv admin tərəfindən qəbul ediləndə atomik tamamlanır; əvvəlki owner `ADMIN` qalır.
9. Room owner qeydiyyatlı müştərini səbəb göstərərək yalnız öz otağında block edə bilər; business owner/admin həmin block-u revoke edə bilər. Block canlı və planlı yeni girişlərə tətbiq olunur.
10. Platform admin hesab, business, room, aktiv subscription, tamamlanmış coin və tarixi subscription ödənişləri, həmçinin açıq support müraciəti saylarını ümumi overview-da görür. Admin qərarları dəyişməz platform audit event-i yaradır.
11. Business owner/admin bütün biznes üzrə, room owner isə səlahiyyətli otaq üzrə tarix aralığına bağlı operational statistikaları görə bilər.
12. Hesabat canlı növbə, planlı booking, completed/cancelled/skipped/removed/reset, guest/registered iştirakçı, təxmini gözləmə, busiest day/hour və təxmini capacity göstəricilərini saxlayır.
13. Operational hesabat `.xlsx` kimi endirilə bilər. Maliyyə gəliri, profit və average receipt Step 6 hesabatına daxil deyil.
14. Guest şəxsi məlumatları retention job ilə 24 aydan sonra anonymize edilir, əməliyyat statistikası isə saxlanılır.
15. Köhnə email əsaslı auth/registration/payment/queue API-ləri `410 Gone` qaytarır; compatibility flag yalnız local və test mühitləri üçündür, production-da qadağandır.
16. Tamamlanmış canlı giriş və ya planlı booking yalnız ona bağlı qeydiyyatlı müştəri tərəfindən bir dəfə qiymətləndirilir. Eyni rating ilk yaradılmadan sonra yeddi gün ərzində edit edilə bilər.
17. Public otaq cavabı yalnız average score və rating count göstərir; yazılı şərhlər yalnız səlahiyyətli room/business idarəçilərinə açıqdır.

## 12.1. Public discovery və frontend contract

1. Platform idarə olunan biznes kateqoriyaları `business_categories` cədvəlində saxlanılır. Biznes optional kateqoriya və yalnız `OTHER` kateqoriyası üçün optional xüsusi alt kateqoriya saxlaya bilər.
2. `GET /api/public/categories` aktiv kateqoriyaları sabit display sırası ilə qaytarır.
3. `GET /api/public/rooms` yalnız `PUBLISHED` və `PUBLIC` otaqları səhifələnmiş formada qaytarır. Səhifə ölçüsü 1-24 aralığında məhduddur və nəticə ad, sonra ID üzrə deterministik sıralanır.
4. Public discovery axtarışı otaq, biznes, filial, ünvan, şəhər, rayon, kateqoriya, xüsusi alt kateqoriya və aktiv xidmət adı üzrə işləyir; category, city, district və reservation mode ayrıca filter ola bilər.
5. `GET /api/public/rooms/{roomId}` `PUBLISHED` otaq profilini qaytarır. `UNLISTED` otaq birbaşa linklə görünür, `PRIVATE` otaq isə public API-də `404` qaytarır.
6. Public profil daxili room/branch qeydlərini, user ID-lərini və gizli telefonları göstərmir. Aktiv owner-in telefonu yalnız həmin assignment üçün `showPhonePublicly=true` olduqda görünür; əks halda biznes otağında filial və ya biznes telefonu effective contact olur.
7. `GET /api/public/qr/{token}` aktiv, ləğv edilməmiş daimi QR tokenini public/unlisted otağın ID-si, reservation mode-u və public frontend yolu ilə resolve edir. Private, draft, inactive, archived və revoked QR public olaraq açılmır.
8. `GET /api/users/me` aktiv bearer sessiyası əsasında cari vahid user profilini access token və password hash kimi həssas sahələrsiz qaytarır.

## 12.2. Hədəf modeldə qalan işlər

1. Biznes və otaq dəvətləri provider-neutral HTTP SMS gateway ilə göndərilə bilər. Email, push, növbə və booking bildirişləri hələ yoxdur.
2. Business-wide calendar görünüşü və room-owner dəyişiklik bildirişləri gələcək mərhələdədir.
3. Service revenue, average receipt və profit kimi maliyyə analitikası customer-service payment modeli qurulandan sonra əlavə ediləcək.
4. Avtomatik telefon təsdiqi və SMS əsaslı password recovery yoxdur; identity və telefon dəyişmə support tərəfindən manual idarə olunur.
5. Köhnə legacy guest qeydlərində telefon yoxdursa həmin tarixçə avtomatik vahid hesaba bağlana bilməz.

## 12.3. Platform admin idarəetməsi

1. İlk platform admin hesabı konfiqurasiyadakı BCrypt credential ilə yalnız hesab mövcud olmadıqda yaradılır. Production mühitində default credential istifadəsinə icazə verilmir.
2. Platform admin ayrıca admin hesabları yarada bilər. Şifrələr plain text saxlanılmır və admin siyahısı şifrə hash-i qaytarmır.
3. Admin istifadəçiləri səhifələnmiş və axtarışlı siyahıda coin balansı ilə görür. Manual coin əlavəsi səbəb və idempotency key tələb edir; təkrar sorğu ikinci dəfə balans artırmır.
4. Admin abunəlik qeydi olan biznesin otaq limitini yalnız artıra bilər. Limit mövcud otaq sayından az və `1000`-dən çox ola bilməz.
5. Manual artırılmış otaq limiti növbəti aylıq coin ödənişində standart `5` otağa geri qaytarılmır.
6. Admin hesabının yaradılması, coin əlavəsi və otaq limiti artımı platform audit jurnalına yazılır.
7. Bütün admin idarəetmə endpoint-ləri həm security chain, həm də use-case girişində `ADMIN` səlahiyyəti tələb edir.

## 12.4. Coin production keçidi

1. `/api/subscriptions/checkout` və köhnə subscription payment read/confirm/cancel endpoint-ləri autentifikasiyadan sonra həmişə `410 Gone` qaytarır.
2. Payment reconciliation yalnız local/test legacy registration sessiyalarını əhatə edə bilər; provider subscription sessiyası üçün bank provayderinə çıxmır və production default olaraq bağlıdır.
3. Keçid miqrasiyası yarımçıq `PROVIDER_SUBSCRIPTION` bank sessiyalarını `CANCELLED` edir və xarici order şifrəsini silir. Tamamlanmış tarixi qəbzlər silinmir.
4. Production rejimi legacy API və payment reconciliation aktivdirsə işə düşmür. Bank kartı ilə balans artırma ayrıca gələcək inteqrasiya kimi qalır.

## 13. Dəyişiklik zamanı qorunacaq qaydalar

- Fərdi hesabın bir növbə limiti pozulmamalıdır.
- Korporativ sahib bütün öz növbələrini, manager yalnız öz növbəsini idarə etməlidir.
- Qeydiyyatsız qoşulmada ad-soyad məcburi qalmalıdır.
- Müştərinin nömrəsi və gözləmə vaxtı serverdə hesablanmalıdır; frontend yalnız göstərməlidir.
- Bank callback query parametri ödənişin uğurlu sayılması üçün kifayət etməməlidir.
- Profil `EXPIRED` olduqda ona bağlı bütün növbələr dərhal deaktiv olmalıdır.
- Tarixçə növbə sıfırlandıqda silinməməlidir.
