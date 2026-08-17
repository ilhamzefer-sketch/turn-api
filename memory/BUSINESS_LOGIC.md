# E-Növbə biznes məntiqi

## 1. Məhsulun məqsədi

E-Növbə fiziki növbəni rəqəmsallaşdırır. Növbə yaradan tərəf xidmət nöqtəsini və növbəni idarə edir. Müştəri QR kodu oxudaraq, UID kodunu yazaraq və ya kabinetdən uzaqdan qoşularaq nömrə götürür, qarşısında neçə nəfər olduğunu və təxmini gözləmə vaxtını görür.

## 2. Rollar

### Vahid istifadəçi (`USER`)

- Yeni əsas hesab modelidir və Azərbaycan telefon nömrəsi ilə tanınır.
- `0501234567`, `501234567` və `+994501234567` eyni `+994501234567` identifikatoruna normallaşdırılır.
- Eyni normallaşdırılmış telefon yalnız bir `USER` hesabına aid ola bilər.
- Hesab pulsuz yaradılır və gələcəkdə eyni profil müştəri, individual specialist, business sahibi/admini və room owner kontekstlərində istifadə ediləcək.
- Hazırkı keçid dövründə köhnə `REGISTRATION`, `CUSTOMER` və `QUEUE_MANAGER` hesabları işləməyə davam edir.

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

## 3. Növbə yaradanın qeydiyyat və ödəniş axını

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
2. Telefon yalnız Azərbaycan `+994` nömrəsi kimi normallaşdırılır və unikal saxlanır.
3. Telefon mövcud deyilsə pulsuz `ACTIVE` hesab yaranır.
4. Telefon `PENDING` hesabına aiddirsə eyni hesab aktivləşdirilir; yeni hesab yaradılmır və dəvətdə yazılmış ilkin adlar audit məlumatı kimi qorunur.
5. Telefon `PASSWORD_RESET_REQUIRED` hesabına aiddirsə eyni hesaba yeni şifrə qoyulur və əvvəlki bütün sessiyalar ləğv edilir.
6. Aktiv hesab üçün təkrar qeydiyyat rədd edilir.
7. `POST /api/auth/login` telefon və şifrə ilə vahid girişdir.
8. Beş ardıcıl səhv şifrə hesabı telefon səviyyəsində 15 dəqiqə kilidləyir.
9. `USER` öz aktiv refresh sessiyalarını görə, konkret sessiyanı, digər sessiyaları və ya bütün sessiyaları ləğv edə bilər.
10. Köhnə email əsaslı endpoint-lər frontend migrasiyası tamamlanana qədər compatibility üçün saxlanılır.

Yeni şifrələr 8-128 simvol qəbul edir, geniş istifadə olunan zəif şifrələr rədd olunur və SHA-256 pre-hash üzərindən BCrypt ilə saxlanılır.

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
10. Otaq təyinatı biznes üzvlüyündən ayrıca qəbul edilir. Pending biznes üzvlüyü qəbul edilmədən otaq dəvəti aktivləşmir.
11. Hər otaq sahibi öz telefonunun həmin otağın public səhifəsində göstərilib-göstərilməməsini özü seçir.
12. Workspace switcher müştəri, individual workspace, idarə edilən biznes və qəbul edilmiş otaq kontekstlərini qaytarır.
13. Filial aktiv otaqları olduğu müddətdə arxivləşdirilə bilməz. Otaq silinməsi fiziki delete deyil, `ARCHIVED` statusudur.

Pending hesab yaradılması üçün başlanğıc limitlər biznesə gündə `500`, dəvət edən idarəçiyə gündə `100` və dəqiqədə `20`-dir. Dəyərlər environment konfiqurasiyası ilə dəyişdirilə bilər.

## 5.2. Legacy növbə qaydaları

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
- Ödəniş təsdiqi server-to-server bank cavabı ilə aparılır.
- Rate limit auth, payment və public queue endpoint-lərinə tətbiq olunur; stage/prod mühitində Redis istifadə edilir.
- PostgreSQL və Redis portları internetə açılmamalıdır.
- Növbə sahibi, müştəri və queue manager ID-ləri request body-dən etibarlı sayılmır; JWT istifadəçisi ilə əvəz edilir.

## 12. Hazırkı implementasiya ilə hədəf model arasındakı fərqlər

Bu maddələr gələcək biznes işi kimi qalır:

1. Hazırkı ödəniş bir dəfəlik qeydiyyat ödənişidir. Hədəf model aylıq abunəlik, növbəti ödəniş tarixi, grace period və yenilənmə tarixçəsi tələb edir.
2. Admin aylıq gəliri hazırda tamamlanmış payment session-ların tarixinə görə hesablayır. Tam abunəlik modelində ayrıca dəyişməz payment ledger yaradılmalıdır.
3. Business, branch, individual workspace, business membership, room və room assignment modeli hazırdır. Həftəlik availability, publish validation və planlı slot hesablanması növbəti development mərhələsində əlavə ediləcək.
4. Email/SMS/push növbə bildirişləri hələ yoxdur.
5. Növbədən çıxma, çağırışı ötürmə, no-show və xidmətin tamamlanması ayrıca statuslarla modelləşdirilməyib.
6. Köhnə guest qeydlərində telefon yoxdur; yalnız telefonla yaradılan yeni qeydlər avtomatik hesaba bağlana bilər.

## 13. Dəyişiklik zamanı qorunacaq qaydalar

- Fərdi hesabın bir növbə limiti pozulmamalıdır.
- Korporativ sahib bütün öz növbələrini, manager yalnız öz növbəsini idarə etməlidir.
- Qeydiyyatsız qoşulmada ad-soyad məcburi qalmalıdır.
- Müştərinin nömrəsi və gözləmə vaxtı serverdə hesablanmalıdır; frontend yalnız göstərməlidir.
- Bank callback query parametri ödənişin uğurlu sayılması üçün kifayət etməməlidir.
- Profil `EXPIRED` olduqda ona bağlı bütün növbələr dərhal deaktiv olmalıdır.
- Tarixçə növbə sıfırlandıqda silinməməlidir.
