# Plan projekta: IPTV plejer za Android telefon i Android TV

Stanje: **Faze 0 do 5 urađene**. Faza 6 (TV) je prošla pregled koda i čeka probu na TV-u. Faza 7 je u toku:
urađena su podešavanja plejera i dodavanje liste preko telefona.

Ovaj dokument je izvor istine za obim i redosled rada. Kad se neka stavka završi,
označi je sa `[x]`. Kad se odluka promeni, izmeni je ovde, ne samo u razgovoru.

---

## 1. Cilj

Sopstvena IPTV aplikacija za Android telefon i Android TV, po funkcijama slična
aplikacijama Hot Player Plus, TiViMate i Smarters TV Pro.

**Principi koji se ne menjaju:**

- Aplikacija je **potpuno besplatna**: bez probnog perioda, bez pretplate, bez reklama.
- Aplikacija **ne sadrži nikakav sadržaj**. Korisnik sam dodaje svoje liste
  (M3U ili Xtream Codes nalog). U kodu i repou nema ugrađenih lista, kanala ni linkova.
- Bez praćenja korisnika i bez analitike. Podaci o listama ostaju na uređaju.
- Kod je **originalan**. Postojeće aplikacije služe samo kao inspiracija za funkcije.
  Ne dekompajliramo ih i ne kopiramo njihov kod, ikonice, logotipe, nazive ni izgled ekrana.

## 2. Donete odluke

| Tema | Odluka |
|---|---|
| Jezik | Kotlin |
| UI | Jetpack Compose (telefon) + Compose for TV (`androidx.tv`) |
| Plejer | AndroidX Media3 (ExoPlayer), sa HLS, DASH i MPEG-TS podrškom |
| Baza | Room |
| Mreža | OkHttp + Retrofit + kotlinx.serialization |
| Slike (logotipi kanala) | Coil |
| Podešavanja | DataStore |
| DI | Hilt |
| Asinhroni rad | Coroutines + Flow |
| Pozadinsko osvežavanje | WorkManager |
| Navigacija | Navigation 3 (`androidx.navigation3`) |
| Build | Gradle (Kotlin DSL) + version catalog (`gradle/libs.versions.toml`) |
| Isporuka | Jedan APK za telefon i TV |
| Gde se bilduje | **Samo GitHub Actions** (vidi odeljak 5) |
| Repo | Javan od 16. 11. 2026, `mcbane23/iptv-player` (GitHub Actions je za javne repoe bez limita minuta; tajne ostaju samo u GitHub Secrets) |
| Način rada | Claude Code na webu i u Android aplikaciji |
| Package name (applicationId) | `com.bane23.iptvplayer` (potvrđeno) |
| Jezici interfejsa | Engleski i srpski (svi tekstovi u `strings.xml`) |
| Tema | Tamna, sa jednom akcentnom bojom |

Verzije biblioteka se ne zakucavaju u ovom planu. U Fazi 0 uzeti najnovije **stabilne**
verzije i upisati ih u version catalog.

Predlog SDK nivoa: `minSdk 24`, `targetSdk` i `compileSdk` na najnovijem stabilnom.

## 3. Otvorene odluke (pitati vlasnika pre nego što zatrebaju)

- [x] **Ime aplikacije.** Rešeno 16. 11. 2026: „Glowbox” (prvo je bilo „Ember”, Bane promenio istog dana). Paket
      (`applicationId`) ostaje isti, da nadogradnja ne obriše podatke.
- [x] **Ikonica.** Akcentna boja je rešena 16. 11. 2026: korisnik je bira u Podešavanjima
      (`AccentColor`), podrazumevana je svetlo narandžasta. Ikonica rešena 16. 11. 2026: ekran
      sa znakom za puštanje i postoljem; TV baner je ta slika + „Glowbox”. Od 19. 11. 2026.
      pozadina ikonice i banera je tamno teget, sa toplim sjajem iza ekrana.
- [ ] Da li je potrebna distribucija van GitHub-a (npr. Google Play). Utiče na Fazu 8.
- [x] **Sinhronizacija preko naloga (Faza 9).** Rešeno 16. 11. 2026: sopstvena prijava
      (Google ili email) nije potrebna. Odgledano i pozicija gledanja idu preko korisnikovog
      Trakt naloga; favoriti i liste ostaju samo na uređaju (prenos telefon→TV iz Faze 7).

## 4. Arhitektura

Jedan Gradle modul (`app`) dok projekat ne naraste. Paketi su podeljeni po slojevima:

```
app/src/main/java/<package>/
  data/
    db/          Room: entiteti, DAO, baza
    m3u/         M3U parser
    xtream/      Xtream Codes API klijent i modeli
    epg/         XMLTV parser i skladištenje vodiča
    repo/        Repozitorijumi (jedini ulaz za UI sloj)
    prefs/       DataStore podešavanja
  domain/        Modeli koje koristi UI (Playlist, Channel, Category, Program...)
  player/        Media3 omotač, upravljanje sesijom, izbor zvuka i titlova
  ui/
    mobile/      Ekrani za telefon
    tv/          Ekrani za TV (fokus, D-pad)
    common/      Deljene komponente, tema, ViewModel-i
  work/          WorkManager poslovi (osvežavanje liste i EPG-a)
  di/            Hilt moduli
```

**Telefon i TV u jednom APK-u:**

- Manifest deklariše i `LAUNCHER` i `LEANBACK_LAUNCHER` intent filter, TV baner i
  `android.software.leanback` / `android.hardware.touchscreen` kao `required="false"`.
- Pri pokretanju se proverava da li je uređaj TV (`UiModeManager`), pa se učitava
  `ui/tv` ili `ui/mobile` navigacija.
- ViewModel-i, repozitorijumi i plejer su zajednički. Razlikuje se samo UI sloj.

**Model podataka (Room), početna verzija:**

- `playlists`: id, naziv, tip (M3U_URL, M3U_FILE, XTREAM), url/server, korisničko ime,
  lozinka, EPG url, vreme poslednjeg osvežavanja
- `categories`: id, playlistId, naziv, tip (LIVE, VOD, SERIES), redosled
- `channels`: id, playlistId, categoryId, naziv, logo, streamUrl, tvgId, broj kanala,
  catchup podaci
- `watch_progress`: dokle se stiglo sa filmom ili epizodom (Faza 5). Spiskovi filmova i serija se ne
  čuvaju u bazi, učitavaju se po kategoriji kad zatrebaju.
- `favorites`: playlistId, tip, itemId, redosled
- `history`: itemId, tip, poslednja pozicija, vreme gledanja
- `epg_programs`: tvgId, početak, kraj, naslov, opis (indeks po tvgId + početak)

Lozinke Xtream naloga čuvati šifrovano (Android Keystore), ne kao običan tekst.

Nadogradnja aplikacije ne sme da obriše liste, favorite ni istoriju: od verzije baze 5 svaka promena
šeme ide sa migracijom (`data/db/Migrations.kt`).

## 5. Build i CI (GitHub Actions)

U cloud okruženju Claude Code-a pristup Google i Maven serverima može biti blokiran,
pa lokalni Gradle build ne radi. **Svaka provera builda ide preko GitHub Actions.**

**`.github/workflows/build.yml`** (pokreće se na svaki push; poseban pull_request build ne postoji,
jer PR prikazuje rezultat push builda za isti commit). Dva posla rade paralelno:

- **Test APK**: checkout, `actions/setup-java` (Temurin 21), `gradle/actions/setup-gradle`
  (keširanje), `./gradlew assembleDev`, na main-u upload APK-a kao artifact i objava pre-release-a.
  Build `dev` je optimizovan kao izdanje (R8, nije debuggable; odluka vlasnika 2026-11-16, debug build
  je bio spor na slabijem TV-u). Na main-u je potpisan istim ključem kao zvanična izdanja (odluka
  vlasnika 2026-11-16), a na granama debug ključem
- **Testovi i lint**: isti koraci za pripremu, `./gradlew testDebugUnitTest lintDebug`,
  upload izveštaja

CI je zelen tek kad oba posla prođu. Pre-release se objavljuje čim je APK gotov, ne čeka testove.

APK artifact se čuva samo za build-ove sa `main`-a. Push na `main` dodatno
objavljuje APK kao pre-release `dev-latest` (stalan link, zgodno za TV). Grane ne objavljuju APK
(odluka vlasnika 2026-11-16, linkovi po grani su bili zbunjujući); na njima build i testovi
služe samo kao provera. Ažuriranje iz aplikacije prati samo `main`. Objavljeni APK je
**potpisan istim ključem kao zvanična izdanja**.

**Ažuriranje iz aplikacije (urađeno, dev kanal):** CI APK sa `main`-a objavljuje i u
poseban javni repo za APK-ove (`mcbane23/apps`, izdanje `iptv-player-dev`, fajlovi
`iptv-player-dev.apk` i `iptv-player.json` sa `versionCode`, `versionName` i `sha256`). Repo je
zajednički za više aplikacija, svaka ima svoje tagove. CI piše tamo tokenom `APK_REPO_TOKEN`
(fine-grained, Contents: Read and write samo na taj repo); bez secreta korak se preskače.
`versionCode` je broj CI run-a, lokalni build je 1. Aplikacija pri startu tiho proverava
manifest, a u podešavanjima postoji „Proveri ažuriranje”; preuzima APK, proverava heš i
predaje ga sistemskom instaleru (`PackageInstaller`). Android traži jednu potvrdu, osim na
Android 12+ kad je aplikacija već sama sebe instalirala. Odluka vlasnika: do v1.0 ažuriranje prati
dev buildove, posle Faze 8 prelazi na zvanična izdanja (novi ključ znači jedno ručno instaliranje).

**`.github/workflows/release.yml`** (odloženo do Faze 8, kad vlasnik doda secrets;
pokreće se na tag `v*`):

1. isti koraci za pripremu
2. dekodiranje keystore-a iz secreta
3. `./gradlew assembleRelease`
4. objava potpisanog APK-a u GitHub Releases

**GitHub Secrets za release** (vlasnik ih dodaje kasnije, uputstvo ide u README u Fazi 8):

- `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`

**Stalan debug ključ (potvrđeno):** `keystore/debug.keystore` je u repou i koristi se za
svaki debug build, lokalno i u CI-ju, da bi novi APK mogao da se instalira preko starog.
Podaci su standardni Android debug podaci: store i key lozinka `android`, alias
`androiddebugkey`. U Fazi 0 ga povezati u `app/build.gradle.kts`
(`signingConfigs.getByName("debug")` sa `storeFile = rootProject.file("keystore/debug.keystore")`).
Ovaj fajl se ne menja i ne generiše ponovo. Koristi se samo za debug, nikad za release.

Dok secrets ne postoje, nijedan workflow ne sme da zavisi od njih: build mora da
prolazi i bez njih.

Release keystore se **nikada** ne komituje u repo. Jednom napravljen ključ mora da se
sačuva na sigurnom, jer bez njega nema ažuriranja već instalirane aplikacije.

**Gradle wrapper:** `gradlew`, `gradlew.bat` i `gradle/wrapper/*` moraju biti u repou.
Wrapper se generiše komandom `gradle wrapper` (ne zahteva mrežu ako je Gradle instaliran).

**Kako ispravljati greške u buildu bez lokalnog builda:**

- posle push-a: `gh run list --limit 3`, zatim `gh run view <id> --log-failed`
- ispraviti, pushovati, ponoviti dok CI ne prođe
- faza se ne smatra završenom dok CI nije zelen

## 6. Faze

Redosled je namerno takav da se posle svake faze dobije APK koji može da se instalira
i proba. Jedna faza = jedna grana + pull request.

### Faza 0: Kostur projekta i CI

Cilj: prazan ekran „Hello” koji se bilduje na GitHub-u i instalira na telefon i TV.

- [x] Gradle projekat (Kotlin DSL, version catalog, wrapper)
- [x] `app` modul sa Compose-om, Hilt-om i praznom navigacijom (Navigation 3)
- [x] Manifest za telefon + TV (oba launcher-a, TV baner)
- [x] Tema (tamna), `strings.xml` za srpski i engleski
- [x] Debug potpis povezan na `keystore/debug.keystore` iz repoa
- [x] `build.yml` workflow, debug APK kao artifact
- [x] `.gitignore`, ažuriran README sa uputstvom za preuzimanje APK-a
- [ ] **Provera:** CI zelen, APK instaliran na telefon i TV, aplikacija vidljiva u oba launcher-a

### Faza 1: Liste (playlists)

Cilj: korisnik može da doda, osveži i obriše listu, a sadržaj se upisuje u bazu.

- [x] Room baza: `playlists`, `categories`, `channels`
- [x] **M3U parser**: `#EXTM3U`, `#EXTINF` sa atributima `tvg-id`, `tvg-name`,
      `tvg-logo`, `group-title`, `tvg-chno`, `catchup*`; `url-tvg` / `x-tvg-url` iz zaglavlja;
      `#EXTVLCOPT` i `#EXTGRP`; tolerancija na loše formirane redove
- [x] Parser radi strimovano (liste mogu imati desetine hiljada stavki), upis u bazu u paketima
- [x] **Xtream Codes klijent** (`player_api.php`): prijava i podaci o nalogu,
      `get_live_categories`, `get_live_streams`; sastavljanje URL-a strima
- [x] Ekran „Dodaj listu”: M3U link, M3U fajl sa uređaja, Xtream prijava
- [x] Ekran sa spiskom lista: osveži, izmeni, obriši, izaberi aktivnu
- [x] Prikaz grešaka razumljiv korisniku (pogrešan link, istekao nalog, nema mreže), sa tehničkim detaljem ispod poruke
- [x] M3U link oblika `get.php?username=..&password=..` se prepoznaje i dodaje kao Xtream nalog
- [x] Ako https veza sa serverom ne uspe, proba se http i pamti adresa koja radi
- [x] Unit testovi za M3U parser i Xtream modele (sa izmišljenim primerima, bez pravih lista)

### Faza 2: Live TV i plejer

Cilj: izbor kategorije, izbor kanala, gledanje.

- [x] Ekran kategorija i kanala (telefon: lista; TV: kolone sa fokusom)
- [x] Media3 plejer: HLS, MPEG-TS, DASH, progresivni strim
- [x] Kontrole: pauza, sledeći/prethodni kanal, lista kanala preko slike
- [x] Izbor audio zapisa i titlova, odnos stranica (fit / fill / zoom)
- [x] Automatski ponovni pokušaj kad strim pukne, jasna poruka kad ne može da se pusti
- [x] Podešavanje User-Agent-a po listi (neki provajderi ga zahtevaju)
- [x] Ekran ostaje upaljen tokom gledanja; pravilno ponašanje pri izlasku i povratku
- [x] Telefon: rotacija, jačina zvuka i osvetljenje (od 16. 11. 2026. dve tanke trake uz ivice umesto prevlačenja bilo gde; od 17. 11. 2026. svaka se posebno uključuje u Podešavanjima, podrazumevano su isključene; od 22. 11. 2026. trake su uklonjene: prevlačenje gore-dole uz levu ivicu menja osvetljenje, uz desnu zvuk, radi i bez kontrola na ekranu, a vrednost pokazuje mala pločica gore u sredini; oba prevlačenja su uključena dok se ne isključe u Podešavanjima), slika u slici (PiP; od 17. 11. 2026. mali prozor ima dugme za pauzu i puštanje)
- [x] Pauza i puštanje (17. 11. 2026): sadržaj koji je korisnik pauzirao ostaje pauziran kad se aplikacija vrati iz pozadine; na telefonu dupli dodir na sliku pauzira i pušta (u filmovima i serijama srednja trećina, leva i desna i dalje premotavaju); kad se slušalice odvoje (Bluetooth ili kabl), plejer se pauzira
- [x] Telefon: puštanje u pozadini (17. 11. 2026), opcija u Podešavanjima, podrazumevano isključena: zvuk nastavlja kad se ekran ugasi ili aplikacija ode u pozadinu, uz obaveštenje sa pauzom i zaustavljanjem (rade i dugmad na slušalicama). Pauziran sadržaj se i dalje zaustavlja, a zatvaranje malog prozora (PiP) zaustavlja puštanje. Na TV-u opcije nema
- [x] TV: D-pad gore/dole menja kanal, OK otvara listu, brojevi biraju kanal

### Faza 3: Favoriti, istorija, pretraga

- [x] Dodavanje i uklanjanje favorita, posebna kategorija „Favoriti”
- [x] „Nedavno gledano” i pokretanje poslednjeg kanala pri startu (opciono u podešavanjima)
- [x] Pretraga kanala po nazivu kroz sve kategorije
- [x] Sakrivanje i promena redosleda kategorija

### Faza 4: TV vodič (EPG)

- [x] XMLTV parser (strimovan, podrška za `.gz`), upis u `epg_programs`
- [x] Izvor: EPG link iz liste, ručno unet link, ili Xtream `xmltv.php`
- [x] Povezivanje programa sa kanalima preko `tvg-id` (uz rezervno poklapanje po nazivu)
- [x] „Sada / Sledeće” u listi kanala i u plejeru
- [x] Ekran vodiča u obliku mreže (vremenska osa), prilagođen i telefonu i TV-u
- [x] Periodično osvežavanje preko WorkManager-a, brisanje starih programa
- [x] Vremenski pomak (time shift) za EPG po listi

### Faza 5: Filmovi i serije (samo Xtream)

- [x] `get_vod_categories`, `get_vod_streams`, `get_vod_info`
- [x] `get_series_categories`, `get_series`, `get_series_info` (sezone i epizode)
- [x] Ekrani sa posterima, detalji, puštanje
- [x] Nastavak gledanja od zapamćene pozicije, sledeća epizoda

### Faza 6: Doterivanje TV interfejsa

Pregled koda je urađen: ispravljena su mesta gde je fokus mogao da nestane (isključena ili zamenjena
dugmad, obrisani redovi), fokus se posle povratka vraća na poslednji kanal ili poster, a red dugmadi na
početnom ekranu se pomera ako ne stane. Stavke ostaju otvorene dok se ne potvrde na pravom TV-u.

- [ ] Pregled svih ekrana samo sa daljinskim: fokus nikad ne sme da se izgubi
- [ ] Brzina listanja dugih lista, učitavanje logotipa bez zastajkivanja
- [ ] Margine za overscan, čitljivost sa daljine
- [ ] Proba na pravom Android TV uređaju i na slabijem TV box-u

### Faza 7: Podešavanja i dodatne funkcije

Redosled unutar faze po želji vlasnika.

- [x] Podešavanja plejera (bafer, dekoder hardverski/softverski, podrazumevani jezik zvuka i titlova)
- [x] Pamćenje izbora titlova: ako je korisnik uključio titlove (i izabrao jezik), oni se
      sami uključuju pri sledećem puštanju, bez ponovnog biranja (pamti se jezik, pa titl bez
      oznake jezika ne može da se zapamti)
- [x] Catch-up / arhiva (M3U `catchup` atributi i Xtream `timeshift`): ulaz je iz TV vodiča, ekran
      „Arhiva” prikazuje prošle emisije kanala po danima. Za Xtream spisak daje server; za M3U se pravi
      iz sačuvanog vodiča (čuva se do 7 dana unazad za kanale sa arhivom)
- [x] Roditeljska zaštita PIN-om (zaključavanje kategorija)
- [x] Roditeljska zaštita, pooštrena: „Zaključaj ponovo” zatvara ekran bez traženja PIN-a; uneti PIN važi
      samo dok se ne izađe iz aplikacije (pozadina, gašenje ili uspavljivanje TV-a); kanali zaključanih
      kategorija se nikad ne pamte u „Nedavno”
- [x] Roditeljska zaštita, lakše korišćenje (Bane, 22. 11. 2026; telefon i TV): pretraga kategorija na ekranu
      roditeljske zaštite; dugi pritisak (dugi OK) na kategoriju u Uživo, Filmovima i Serijama nudi „Zaključaj”,
      odnosno „Ukloni zaključavanje”, i brzo „Otključaj sve (PIN)” / „Zaključaj ponovo”; pojedinačan kanal, film
      ili serija se zaključava iz svog menija i ne vidi se nigde dok se ne unese PIN (tabela `locked_items`,
      spisak „Pojedinačno zaključano” na ekranu roditeljske zaštite). Na TV-u dugi OK na poster u Filmovima i
      Serijama otvara meni (omiljeni, zaključaj). „Zaključaj ponovo” više ne prikaže unos PIN-a na trenutak
- [x] „Nedavno gledano”: dugi pritisak (dugo OK) na kanal nudi „Ukloni iz nedavno gledanih”, a na samu
      grupu „Obriši sve nedavno gledane” (telefon i TV)
- [x] Dodavanje liste na TV preko telefona: TV prikaže QR kod, telefon u istoj mreži otvori stranicu sa TV-a
      i pošalje podatke liste (urađeno uz Fazu 6, čeka probu)
- [x] Izvoz i uvoz podešavanja i lista (prebacivanje sa telefona na TV): sa stranice koju otvara QR kod
  aplikacija na telefonu šalje sve liste, omiljene kanale, uređenje kategorija i podešavanja pravo na TV
- [x] Prenos na TV iz podešavanja na telefonu: telefon sam nađe TV u mreži (kod sa ekrana samo prvi put) i šalje
      liste, naloge, omiljene kanale, filmove i serije, sakrivene kategorije i redosled (i za filmove i serije)
      podešavanja, zaključane kategorije i PIN (samo ako ga TV nema); na listi koju TV već ima važi uređenje sa telefona, omiljeni se dopunjuju (čeka probu)
- [x] Prikaz više kanala odjednom (multi-view): 2 ili 4 polja na TV-u i telefonu, zvuk sa izabranog polja
- [x] Automatsko pokretanje pri paljenju uređaja (TV box): prekidač u podešavanjima na TV-u; od Androida 10
      traži i sistemsku dozvolu „Prikaz preko drugih aplikacija”

### Novi izgled (UI revamp)

Odobreno 15. 11. 2026. Funkcije ostaju iste, menja se izgled i raspored. Predlog sa maketama:
https://claude.ai/artifact/BKuSvoRR7m39b1p82H9bi4. Dok ovo traje, nove stavke Faze 7 su pauzirane.
Svaki korak je posebna grana (`faza-uN-...`) i pull request. Korak 3 menja bazu (verzija 6, sa
migracijom: dodaje naziv i poster u zapamćeno gledanje), liste i istorija ostaju.

Odluke: telefon ima donju traku (Uživo, Filmovi, Serije, Podešavanja); TV ima bočni meni koji se
raširi na fokus; Uživo na TV-u ima tri kolone sa malim pregledom kanala (može da se isključi);
u TV plejeru OK prikazuje kontrole, strelica levo listu kanala. Tekst se nigde ne sme preklapati,
a uputstvo za gestove u plejeru se prikazuje samo pri prvom pokretanju.

- [x] 1. Osnova: ikonice, tipografija, donja traka na telefonu, bočni meni na TV-u
- [x] 2. Uživo: kategorije kao čipovi, „sada” uz kanal, pretraga u sekciji, TV u tri kolone sa pregledom
- [x] 3. Filmovi i serije: „Nastavi gledanje”, mreža postera, pretraga kroz sve kategorije, novi detalji
- [x] 4. Plejer, isti izgled na TV-u i telefonu: naslov gore, traka sa vremenom i red dugmadi dole
  (Titlovi, Audio, Format, Od početka). TV: OK prikazuje kontrole, levo/desno premotava (držanje
  ubrzava), levo u kanalima otvara listu. Telefon: dupli dodir premotava, zaključavanje, slika u slici,
  uputstvo za gestove samo pri prvom pokretanju
- [x] 5. Podešavanja i liste: grupisana podešavanja, zaglavlja sa ikonicama, prazna stanja
- [x] 6. Plejer, dorada rasporeda: kontrole na tamnim pločicama bez zatamnjenja slike; gore nazad u
  krugu i naslov u okviru u jednom redu; dole jedna zaobljena traka (premotavanje, puštanje, vreme,
  traka, rezolucija, format), a ispod nje red dugmadi samo sa nazivom. Fokus na TV-u je prsten u boji teme
- [x] 7. Boja aplikacije: izbor akcentne boje u Podešavanjima (šest boja), podrazumevana svetlo
  narandžasta; važi odmah, na telefonu i TV-u
- [x] 8. Veličina prikaza: podešavanje koje umanjuje ceo interfejs (100%, 85%, 75%, na TV-u i 65%),
  da stane više redova i duži nazivi; početna vrednost 75% na TV-u, 100% na telefonu

### Faza 8: Izdanje

- [x] Potvrđeni ime, akcentna boja, ikonica i TV baner
- [x] Keystore napravljen, secrets dodati, `release.yml` radi. `release.yml` pravi izdanje sam kad se
      u main spoji nova verzija u `version.txt`; keystore i `RELEASE_*` secrets su dodati (vidi README).
- [ ] R8/ProGuard pravila proverena na release buildu. R8 i smanjivanje resursa su uključeni i
      release build se pravi u CI-ju na svaki push; ostaje proba release APK-a na uređaju.
- [x] Verzionisanje: ime izdanja iz `version.txt` (`1.2.3` daje oznaku `v1.2.3`), dev build ima
      dodatak `-dev.N`. `versionCode` je isti račun za oba: 1 000 000 + broj commitova u main-u
- [x] README: kako instalirati APK na telefon i na TV
- [x] Prvo izdanje `v1.0.0` u GitHub Releases (2026-11-16)
- [x] Ažuriranje iz aplikacije: zvanična izdanja prate kanal `iptv-player` u javnom repou, dev
      buildovi ostaju na `iptv-player-dev` (`UpdateSource.kt`, `release.yml`).
- [x] Prelazak između dev builda i izdanja bez brisanja (odluka vlasnika 2026-11-16): oba su
      potpisana istim ključem i dele brojanje verzija, a kanal se bira u Podešavanjima („Verzija za
      ažuriranje”). Prelazi se samo na noviji build. Dev buildovi stariji od ove promene (debug
      ključ) traže jednokratno brisanje.
- [x] „Šta je novo” u prozoru ažuriranja pre potvrde, na telefonu i TV-u (odluka vlasnika 2026-11-19):
      test buildovi nabrajaju naslove izmena novijih od instaliranog builda, zvanična izdanja
      prikazuju ručno pisane novosti iz `novosti/<verzija>.sr.txt` i `.en.txt`.

### Faza 9: Naknadne ideje

Ideje dodate posle prvobitnog plana. Rade se posle Faze 8, redosled po želji vlasnika.
Pre početka svake stavke dogovoriti detalje (vidi odeljak 3).

**Jezik**

- [x] Izbor jezika u Podešavanjima (Kao uređaj, Srpski, English), telefon i TV; važi odmah i za
      obaveštenja. Srpski tekstovi se koriste i kad je uređaj na „srpski (latinica)”

**Odgledano i Trakt**

- [x] Ručno označavanje filmova i epizoda kao odgledanih (i cele sezone), kvačica na posterima
      filmova; gledanje do 95% označava samo (baza v8)
- [x] Trakt nalog: prijava kodom (trakt.tv/activate), slanje i preuzimanje odgledanog (obostrano).
      Poklapanje preko TMDB ID-ja iz panela; bez njega stavka ne ide na Trakt (baza v9).
      `TRAKT_CLIENT_ID` je u GitHub Secrets; Client Secret nije potreban (PKCE)
- [x] Preko Trakt-a i nastavak gledanja između TV-a i telefona (pokriva stavku ispod, za ono što
      Trakt prepozna): pozicija ide na Trakt kad se plejer zatvori, a pri sinhronizaciji se
      preuzima novija pozicija sa drugog uređaja (baza v10)
- [x] „Nastavi” kod serije prati ono što je poslednje gledano na bilo kom uređaju (vreme gledanja sa
      Trakt-a); stara pauza se ne upisuje za ono što je posle odgledano; sinhronizacija i pri
      povratku u aplikaciju i pri otvaranju detalja (najviše jednom u 5 minuta)
- [x] Pri sinhronizaciji se započete serije sa Trakt naloga (bar jedna odgledana epizoda, a nisu
      odgledane sve emitovane) dodaju u omiljene serije aktivne liste. Svaka samo jednom: serija
      koju korisnik ukloni iz omiljenih se ne vraća. Od više verzija iste serije kod provajdera
      (npr. po jezicima) dodaje se samo ona gledana u aplikaciji, ako takva postoji

**Sinhronizacija gledanog sadržaja preko naloga**

Vlasnik je 16. 11. 2026. odlučio da sopstvena prijava (Google ili email) nije potrebna, pa je
ta stavka izbačena. Sinhronizacija ide samo preko Trakt naloga (stavke iznad).

- [x] Sklanjanje pojedinih filmova i serija iz reda „Nastavi gledanje” (telefon: dugi pritisak na poster,
  TV: dugo OK). Pozicija ostaje zapamćena; stavka se vraća tek kad se ponovo gleda, a Trakt
  sinhronizacija je ne vraća (baza v12, `watch_progress.dismissedAt`).
- [x] Sinhronizacija pozicije gledanja: film ili serija započeti na TV-u nastavljaju se
      na telefonu od istog mesta, i obrnuto (preko Trakt-a, za ono što Trakt prepozna)

**Premotavanje strima**

- [x] Dugmad napred/nazad skaču po 10 s (podrazumevano), vrednost skoka podesiva u podešavanjima
- [x] Brzo premotavanje na TV-u: držanje D-pad levo/desno ubrzava premotavanje,
      sa pregledom vremena na traci, da se lako stigne do željenog mesta. Plejer premota tek kad
      se strelica pusti, pa se strim ne učitava iznova na svaki korak

**Snimanje sadržaja**

- [x] Snimanje kanala koji se trenutno gleda (dugme „Snimaj” u plejeru), snimak se čuva na uređaju.
      Vlasnik je 16. 11. 2026. izabrao posebnu vezu za snimanje: snima se i kad se promeni kanal ili
      zatvori plejer (servis u pozadini), ali uz gledanje nalog troši dve veze kod provajdera.
      MPEG-TS se prepisuje kako stiže, HLS deo po deo; šifrovan i fMP4 HLS se ne snima (baza v11)
- [x] Zakazano snimanje iz TV vodiča (EPG): buduća emisija se zakazuje (telefon: dugme u detaljima
      emisije; TV: OK na emisiji), a emisija koja je u toku snima se do svog kraja. Pokreće ga budilnik
      (AlarmManager, ne WorkManager, jer mora da krene tačno na vreme i iz pozadine); uređaj mora biti
      upaljen. Zakazana snimanja se vide i otkazuju na ekranu „Snimci”
- [x] Ručno: vlasnik je 16. 11. 2026. izabrao trajanje u plejeru umesto pune forme. Dugme „Snimaj”
      pita koliko dugo: dok ne zaustavim, do kraja emisije, 30 min, 1 h, 2 h ili 3 h. Puna forma
      (kanal, datum, početak, kraj) se ne pravi dok ne zatreba za kanale bez vodiča
- [x] Ekran „Snimci” (u Podešavanjima): puštanje, zaustavljanje, brisanje, prikaz zauzetog prostora
- [x] Podešavanje mesta čuvanja (memorija uređaja, USB/SD) na ekranu „Snimci”, vidi se kad je priključena
      SD kartica ili USB; upozorenje ispod 2 GB slobodnog prostora, a ispod 300 MB snimanje ne kreće
      i ono koje traje staje. Na USB/SD se piše u folder aplikacije (bez dozvola)
- [x] Oznaka na početnom ekranu dok se nešto snima: na telefonu traka iznad donje trake (dodir otvara
      „Snimci”, jedno snimanje se zaustavlja odmah), na TV-u crvena stavka na vrhu bočnog menija
      koja otvara „Snimci”
- [x] Dugmad ispod trake u plejeru imaju ikonice; na telefonu u portretu ostaje samo ikonica (red
      staje u širinu ekrana), u landscape-u i na TV-u ikonica i naziv
- [x] Podešavanja uključeno/isključeno su prekidači (telefon i TV): OK ili dodir menja stanje odmah,
      bez spiska vrednosti

**Cast sa telefona na TV**

- [x] Dugme „Pusti na TV-u” u plejeru na telefonu: kanal, film ili epizoda koja se gleda na telefonu
      nastavlja se na TV-u, a telefon služi kao daljinski (pauza, premotavanje, jačina, sledeći kanal)
- [x] Način: vlasnik je izabrao Chromecast / Google Cast (2026-11-16). Koristi se Google-ov
      podrazumevani prijemnik, bez registracije. Izbor uređaja i daljinski su sopstveni ekrani.
      Ograničenja: traži Google Play servise na telefonu; Chromecast ne pušta sirov MPEG-TS (za kanale
      se šalje HLS adresa) ni sve formate filmova, a strim mora biti dostupan samom uređaju
- [x] Drugi izbor u istom spisku: slanje na ovu aplikaciju na TV-u kroz kućnu mrežu (TV se oglašava
      preko mDNS-a dok je aplikacija otvorena, prvo povezivanje kodom sa ekrana TV-a, TV pušta strim
      svojim plejerom pa rade svi formati). Chromecast uređaji su prvi u spisku

**Izlaz iz aplikacije**

- [x] Izlaz samo na dupli pritisak „Nazad” sa početnog ekrana: prvi pritisak prikazuje
      toast poruku („Pritisnite Nazad još jednom za izlaz”), drugi u roku od ~2 s zatvara aplikaciju.
      Radi isto na telefonu i na TV-u (daljinski)

**Titlovi**

- [x] Izgled titlova u podešavanjima: uključivanje/isključivanje crne pozadine,
      veličina slova i položaj na ekranu (visina od dna)
- [x] Pregled izgleda titlova u samim podešavanjima: probni titl na umanjenoj slici (telefon i TV)
- [x] Više podrazumevanih jezika po redu važnosti: red „Jezici titlova” i red „Jezici zvuka”, a
      svaki otvara tri mesta (prvi, drugi, treći jezik). Uključuje se prvi jezik koji sadržaj ima; ako nema nijednog, titl ostaje
      isključen. Izbor zvuka ili titla u plejeru važi samo za ono što se gleda i više ne menja
      podešavanja (zamenjuje ranije „Pamćenje izbora titlova”). Pretraga na OpenSubtitles stavlja
      izabrane jezike prve, a jezik uređaja, engleski i srodni jezici ostaju kao dodatak.
- [x] Titlovi sa interneta (OpenSubtitles): u plejeru filmova i epizoda spisak titlova ima stavku
      „Potraži online”. Traži se po TMDB ID-ju (za epizodu: serija + sezona + epizoda), a ako ga provajder
      ne šalje, po nazivu očišćenom od oznaka liste. Jezici: jezik titlova iz podešavanja, jezik uređaja i
      engleski; uz srpski idu i hrvatski i bosanski. Izabrani titl se preuzima i trajno čuva na uređaju (isti se
      ne preuzima dvaput; čuva se poslednjih 300) i uključuje, a gledanje se nastavlja sa istog mesta. Izbor
      se pamti po filmu i epizodi, pa je sledeći put titl već uključen; zaboravlja se kad korisnik isključi
      titlove ili izabere drugi zapis. API ključ stiže iz GitHub Secrets
      (`OPENSUBTITLES_API_KEY`); bez njega se opcija ne prikazuje. Prijava na korisnikov nalog
      (Podešavanja → OpenSubtitles) nije obavezna, a daje veći dnevni broj preuzimanja
- [x] Prijava na OpenSubtitles preko telefona: ekran naloga na TV-u prikazuje QR kod, telefon u istoj mreži
      otvori stranicu sa TV-a i tamo se unesu korisničko ime i lozinka (isti mehanizam kao dodavanje liste)

**Brzina na slabijim TV uređajima**

- [x] Ekrani na TV-u se menjaju odmah (bez pretapanja od 700 ms), bočni meni se širi bez animacije,
      vodič u pregledu kanala se ne čita za svaki kanal dok se taster drži

**Doterivanje izgleda (predlozi od 16. 11. 2026)**

Vlasnik je 16. 11. 2026. izabrao da se predlozi rade redom. Prvi krug:

- [x] TV, Uživo: uputstvo „držite OK za favorite” vidi se samo pri prvom otvaranju, nekoliko sekundi
- [x] Kanal bez logoa dobija kratku oznaku (npr. „RTS1”) na zaobljenoj podlozi; film ili serija
      bez postera dobija naziv na tamnom prelivu
- [x] Dok se kanali, filmovi i serije učitavaju, stoje nepomični sivi obrisi umesto praznog ekrana
- [x] Sat na TV-u: u zaglavlju sekcija Uživo, Filmovi, Serije, Liste, Podešavanja i Vodič, i u plejeru
      dok su kontrole otvorene
- [x] Telefon, Uživo: čipovi Favoriti, Nedavno, Svi i čip „Kategorije” (nosi naziv izabrane kategorije)
      umesto jednog dugmeta sa spiskom
- [x] Telefon, Filmovi i Serije: iste prečice, čip Favoriti i čip „Kategorije” (nosi naziv izabrane)
- [x] Spisak kategorija na telefonu ima samo prave kategorije; Favoriti, Nedavno i Svi su samo čipovi
- [x] Filmovi i Serije u dve kolone (TV i položen telefon): levo zakačeni Favoriti i „Nastavi gledanje”
      sa brojem stavki, ispod polje za filtriranje i kategorije koje se skroluju; desno posteri.
      Položen telefon crta sadržaj do ivica, i ispod kamere (može da se isključi: „Cela širina ekrana”)
- [x] Telefon u landscape-u (van plejera): kartice u uskoj traci levo umesto donje trake, a kanali
      u Uživo u dve kolone. Filmovi i serije već imaju više postera u redu
- [x] TV: poster u fokusu je malo veći (bez animacije) i ima podlogu, da se fokus jasno vidi
- [x] TV, pregled kanala: kad slike nema (pregled isključen, kanal zaključan), veliki logo i naziv kanala
- [x] Prazna stanja i greške su ista svuda: ikonica, kratak tekst i, gde ima smisla, dugme
- [x] Vlasnik je preskočio red nedavno gledanih kanala (čip „Nedavno” radi isti posao)
- [x] TV, Filmovi i Serije: iznad postera naziv, kratki podaci i opis filma ili serije u fokusu
      (detalji se traže kad fokus kratko stane); može da se isključi u Podešavanjima
- [x] TV „Početna” (Bane, 18. 11. 2026, uz sliku): prva stavka bočnog menija i ekran na kom se aplikacija
      otvara. Redovi: „Nastavi gledanje” (filmovi i serije zajedno, poslednje gledano prvo; dugi OK
      sklanja iz reda), omiljeni kanali sa emisijom koja je sada na programu (kad omiljenih nema,
      nedavno gledani), nedavno dodati filmovi i nedavno dodate serije (bez sakrivenih i zaključanih
      kategorija). Red bez sadržaja se ne prikazuje. Bez liste se i dalje otvara Uživo
- [x] Pozadina sa blagim sjajem akcentne boje gore levo (vlasnik izabrao varijantu B): svi ekrani
      na TV-u (osim više kanala odjednom) i početni ekrani na telefonu

Doterivanje lista i kategorija (17. 11. 2026, bez novih funkcija):

- [x] Kanali sakrivenih kategorija ne izlaze u „Svi kanali”, pretrazi, ukupnom broju ni pri listanju
      kanala u plejeru; ostaju u Favoritima i Nedavno
- [x] Sakrivanje i redosled kategorija i za Filmove i Serije (pamti se u podešavanjima, bez promene baze);
      ekran „Kategorije” ima kartice Uživo, Filmovi, Serije
- [x] Dugi pritisak na kategoriju (dugi OK na TV-u): Sakrij, Na vrh, Uredi kategorije
- [x] Ekran „Kategorije”: Na vrh, Sakrij sve, Prikaži sve, filtriranje po nazivu, broj kanala;
      kategorije koje su bile sakrivene pri otvaranju stoje na dnu
- [x] Uživo, Filmovi i Serije se otvaraju na kategoriji koja je poslednja bila otvorena (po listi)
- [x] Broj kanala u spisku kategorija na telefonu
- [x] Uređivanje kategorija: „Prikaži sve” i „Sakrij sve” su dugmad na vidiku i na telefonu (uz pretragu važe
      samo za pronađene); na telefonu se redosled menja prevlačenjem za ručku, na TV-u dugme „Premesti” podiže
      kategoriju pa je strelice gore i dole nose kroz spisak
- [x] TV, Uživo: dugme u zaglavlju prelazi na sledeću listu (kad ih ima više)
- [x] Liste: dodir (OK) na listu je bira kao aktivnu; Osveži, Izmeni, Obriši su u meniju („Opcije” na TV-u);
      potvrda brisanja na TV-u nosi naziv liste
- [x] Forma za listu: User-Agent i polja vodiča iza dugmeta „Napredna podešavanja”
- [x] Serije: prelazak sa sezone na epizode vodi na prvu neodgledanu epizodu te sezone
- [x] TV plejer za filmove i epizode: uklonjeno dugme „Od početka” (ostaje na ekranu detalja, pre puštanja)
- [x] TV plejer filmova i serija: traka vremena u fokusu premotava tek posle potvrde sa OK, da se
  strelicama lako stigne do dugmadi desno od nje.
- [x] TV bočni meni: desno ili Nazad ga uvek sklanja, i na praznom ekranu (bez lista), gde je ostajao
  raširen preko teksta.
- [x] Ikonice: ispravljeno crtanje ikonica čiji je deo bio odsečen ili iskrivljen (vodič u TV meniju,
  prethodni kanal u plejeru, obaveštenja).
- [x] Plejer: dugme „Dekoder” zamenjeno dugmetom „Podešavanja”, koje otvara panel „Brza podešavanja”
  preko slike. Telefon: trake za osvetljenje i zvuk, puštanje u pozadini, dekoder, tajmer gašenja.
  TV: odnos slike, dekoder, veličina titlova, tajmer gašenja, bafer. Filmovi i epizode imaju i
  brzinu puštanja. Tajmer gašenja pauzira ono što se gleda i ne pamti se posle gašenja aplikacije.
- [x] TV plejer: odnos slike nema dugme ispod trake (ostaje ikonica na kraju trake i stavka u
  „Podešavanjima”), a plejer kanala na TV-u i telefonu nema dugme „Titlovi”, da red dugmadi stane na ekran.
- [x] Plejer kanala: dugme „Prethodni kanal” vraća na kanal koji je gledan pre ovog (i taster
  „Last channel” na daljinskom).
- [x] Liste: kartica Xtream liste prikazuje do kada nalog važi i broj veza; poslednjih 7 dana je
  istaknuto. Podatak se osvežava kad i lista (baza v13).
- [x] Liste se jednom dnevno same osvežavaju u pozadini (prekidač u Podešavanjima, uključeno
  podrazumevano; liste iz fajla se ne osvežavaju).
- [x] Filmovi i serije: izbor redosleda u kategoriji (kao kod provajdera, nedavno dodato, po nazivu,
  najbolje ocenjeno, najnovije po godini); pamti se posebno za filmove i za serije.
- [x] Vodič: „Podseti me” za emisiju koja još nije počela (na TV-u OK na budućoj emisiji nudi
  podsetnik i snimanje). Kad emisija počne, aplikacija nudi prelazak na kanal, a na telefonu stiže
  i obaveštenje. Podsetnici su u podešavanjima, bez promene baze.
- [x] TV: opcija „Uskladi osvežavanje TV-a sa snimkom” (isključeno podrazumevano). Radi samo kad
  strim javlja broj sličica i kad TV ima odgovarajući režim u istoj rezoluciji.
- [x] Jedna pretraga za kanale, emisije iz vodiča (u toku i buduće), filmove i serije: nova kartica
  „Pretraga” na telefonu, stavka „Pretraga” u bočnom meniju na TV-u. Emisija koja traje pušta kanal,
  buduća dobija podsetnik. Posebne pretrage kanala, filmova i serija ostaju kakve jesu.
- [x] Pomeranje titla u vremenu (plejer filmova i epizoda): stavke „Titl ranije” i „Titl kasnije”
  u panelu „Podešavanja”, korak pola sekunde, do 30 s u oba smera. Važi za titlove preuzete sa
  interneta (OpenSubtitles); titl ugrađen u snimak se ne može pomerati, pa se stavke tada ne
  prikazuju. Pomeraj važi dok se taj film ili epizoda gleda i ne pamti se.
- [x] Telefon: odnos slike više nema dugme ispod trake u plejeru (kanali, filmovi, epizode); menja se
  u panelu „Podešavanja” plejera, kao prva stavka.
- [x] Plejer na telefonu i TV-u ujednačen: panel „Podešavanja” ima iste stavke istim redom (odnos
  slike, dekoder, veličina titlova, pomeranje titla, tajmer gašenja, brzina, bafer), a telefon na
  kraju ima još trake za osvetljenje i zvuk i puštanje u pozadini. Plejer epizoda na telefonu dobio
  je dugme „Sledeća epizoda”. Namerno različito: TV ima odnos slike i rezoluciju na traci i sat,
  telefon zaključavanje ekrana, sliku u slici i slanje na TV.
- [x] Uživo, doterana lista (Bane izabrao predlog 1 od tri sa slikama): na TV-u su kategorije na podlozi
  sa ikonicama uz Omiljene, Nedavno i Sve kanale, nad kanalima je naziv grupe i broj kanala, red kanala
  kaže do kada emisija traje, a desno su kanal, preostalo vreme i emisije koje slede. Telefon: isti naslov
  nad kanalima i kanali na podlozi. Nije urađeno: „sledi” u redu kanala (traži izmenu upita liste) i stalna
  pomoć za tastere (pravilo: uputstva samo prvi put).
- [x] Uživo na telefonu (Bane izabrao predlog D posle druge runde slika): kanali su pločice u dve kolone
  sa logom levo, pa na ekran staje oko 18 kanala umesto 10; omiljeni se menja dugim pritiskom, zvezdica
  samo pokazuje stanje. Položen telefon ima raspored kao TV: levo spisak (pretraga, omiljeni, nedavno, svi
  kanali, kategorije sa menijem na dugi pritisak), desno kanali, bez zaglavlja i bez čipova.
- [x] Položen telefon, Uživo (Bane izabrao predlog 1 od tri sa slikama): naslov nad kanalima je uklonjen,
  a izbor liste, vodič, više kanala i pretraga su red na vrhu spiska levo, pa kanali počinju u istoj
  visini kao spisak i staje red kanala više.
- [x] Uživo, spisak kategorija kao kod filmova i serija (Bane, 18. 11. 2026, uz slike ekrana): na položenom
  telefonu je svaki red zaobljena pločica, izabrani je ceo u akcentnoj boji, a Omiljeni, Nedavno i Svi kanali
  nose broj ispod naziva; na TV-u spisak više nema podlogu, linije ni natpis „Kategorije”. Raspored i kanali isti.
- [x] Podešavanja: slova za jedan stepen sitnija, na telefonu i na TV-u, da ne odskaču od ostatka aplikacije.
- [x] Uživo na telefonu: izabrana kategorija ostaje ista kad se telefon okrene (izbor pamti view model,
  a ne samo ekran koji se pri okretanju iscrtava iznova).
- [x] TV, spisak kategorija kao na telefonu (Bane izabrao opciju 2 od tri sa slikama, 18. 11. 2026): u Uživo,
  Filmovima i Serijama svaki red je zaobljena pločica. Fokus je pun u akcentnoj boji, a izabrana kategorija
  bez fokusa ima akcentni okvir i tekst. Pretraga i redosled ostaju obični redovi.
- [x] Uživo na telefonu po uzoru na TV (Bane izabrao varijantu B sa slika, 18. 11. 2026): kanali su ponovo u
  jednoj koloni, kao redovi sa brojem, logom, nazivom i emisijom „do HH:mm”. Na položenom telefonu desno je
  pregled kao na TV-u: prvi dodir bira kanal i pušta ga u malom pregledu bez zvuka, drugi dodir ili dugme ga
  otvara preko celog ekrana. Uspravno dodir odmah pušta kanal. Spisak grupa sa izborom liste i ikonicama je isti.
- [x] Uživo, dorada (Bane, 19. 11. 2026): na telefonu su logoi kanala manji (32dp) i redovi gušći, čip
  omiljenih je samo zvezdica (i u Filmovima i Serijama), a dok kanal nije izabran u crnom okviru pregleda
  stoji uputstvo, na telefonu i na TV-u. Na TV-u su redovi kanala niži (logo 32dp), pa staje kanal više.
- [x] Uspravan telefon, zvezdica kao prekidač (Bane, 19. 11. 2026): prvi dodir na čip omiljenih prikazuje
  omiljene, drugi vraća na grupu koja je bila izabrana pre toga (Uživo, Filmovi, Serije). Položen telefon i TV
  ostaju isti, jer je tamo ceo spisak kategorija stalno na ekranu.
- [x] Zaštita ekrana od urezivanja slike (Bane, 18. 11. 2026): posle 1 minuta pauze slika se zatamni na
  oko 30%, a kontrole se sklone; prvi taster ili dodir samo vraća sliku. Posle 30 minuta pauze plejer se
  zatvara (pozicija filma ostaje sačuvana, veza kod provajdera se oslobađa). Važi za kanale, filmove i serije,
  na TV-u i telefonu. Multi view drži ekran upaljen samo dok bar jedno polje pušta ili učitava sliku.
- Odbijeno (Bane, 17. 11. 2026): pauza i premotavanje programa uživo (timeshift), nema potrebe.
- Odbijeno (Bane, 17. 11. 2026): pojačanje zvuka preko 100%. Rezervna kopija u fajl je tada takođe bila
  odbijena, ali je Bane 22. 11. 2026. ipak zatražio (stavka ispod).
- [x] Izvoz i uvoz u fajl (Bane, 22. 11. 2026): Podešavanja > Nalozi i uređaji > „Izvoz i uvoz”, na telefonu
  i TV-u. U fajl ide sve: liste sa prijavama (i lista iz fajla, sa sadržajem), omiljeni i imenovane liste
  omiljenih, sakriveni kanali, uređenje kategorija, zaključavanja i otisak PIN-a, istorija i pozicije
  gledanja, podsetnici, OpenSubtitles i Trakt nalog, jezik i sva podešavanja. Snimci i preuzeto ne ulaze.
  Banetove odluke: lozinka se nudi pri svakom izvozu (prazna = čitljiv JSON, uneta = ceo fajl šifrovan);
  uvoz svaki put pita „Dopuni postojeće” ili „Zameni sve”; telefon bira fajl sistemskim biračem, TV piše u
  Download/Glowbox i nudi fajlove iz tog foldera (za fajl koji nije sam napravio traži pristup svim
  fajlovima); dekoder, veličina prikaza i folder snimaka se vraćaju samo na istoj vrsti uređaja. Postojeći
  PIN i zaključavanja uvoz nikad ne skida. Format: `data/backup/Backup.kt`, oslonjen na `TransferBundle`.
  Dok nema nijedne liste, pored „Dodaj listu” (Uživo i Liste, telefon i TV) stoji i „Vrati iz fajla”, koje
  otvara isti ekran. Fajl se zove `glowbox-export-<datum>.json` (Bane: naziv na engleskom).
  TV ima i sopstveni pregledač fajlova („Izaberi fajl iz drugog foldera”, Bane 22. 11. 2026): unutrašnja
  memorija i priključeni USB, folderi i `.json` fajlovi, Nazad vraća folder naviše.
- [x] Program kanala u plejeru (Bane, 18. 11. 2026, treći krug predloga): dugme „Program” otvara raspored
      kanala koji se gleda (emisija u toku i narednih 24 sata) preko slike, na TV-u i telefonu. Buduća
      emisija nudi „Podseti me” i zakazivanje snimanja, tekuća snimanje do kraja; kanal sa arhivom ima
      prvi red „Arhiva” (arhiva zamenjuje plejer, da kanal ne drži vezu u pozadini)
- [x] Spajanje lista (Bane, 18. 11. 2026; izabrao „samo kanali uživo”): u opcijama liste stavka „Spoji sa
      drugim listama”. Kad je aktivna lista spojena, kanali, kategorije, omiljeni, nedavno gledani, vodič
      i pretraga svih spojenih lista prikazuju se zajedno (liste jedna za drugom). Podrazumevano nijedna
      lista nije spojena, pa se vidi samo aktivna. Filmovi i serije su uvek iz aktivne liste. Baza v14
      (`playlists.merged`). Oznaka se ne prenosi pri prenosu na TV; roditeljska zaštita nudi samo
      kategorije aktivne liste
- [x] Spajanje lista jednim prekidačem (Bane, 22. 11. 2026): umesto stavke u opcijama svake liste, na vrhu
      ekrana Liste (telefon i TV) stoji jedan prekidač „Spoji kanale svih lista” sa opisom. Uključen:
      kanali svih lista zajedno, bez obzira na to koja je aktivna; isključen: samo aktivna lista. Nova
      lista se sama pridružuje spoju. Baza v20: ko je imao spojene bar dve liste dobija sve spojene.
      Prenos na TV prenosi i stanje prekidača
- [x] Detalji filma i serije (Bane, 18. 11. 2026, treći krug predloga): kad provajder šalje široku sliku
      (`backdrop_path`), ona je pozadina preko celog ekrana (TV), odnosno zaglavlja (telefon); inače
      ostaje poster kao do sada. Žanrovi su pločice, ocena je prva u redu podataka, a dugme „Trejler”
      (kad panel šalje `youtube_trailer`) otvara trejler u YouTube-u ili pregledaču
- [x] „Nastavite sa gledanjem” na početnom ekranu TV-a (Bane, 18. 11. 2026, treći krug predloga): do 10
      započetih filmova i serija aktivne liste upisuje se u red Android TV / Google TV početnog ekrana
      (sistemski `TvContract.WatchNextPrograms`, bez nove biblioteke); izbor otvara detalje u aplikaciji.
      Prekidač u Podešavanjima na TV-u, podrazumevano uključen. Na Fire TV-u i telefonima ne radi ništa
- [x] Kartica „Sledeća epizoda” (Bane, 18. 11. 2026, treći krug predloga): 20 sekundi pre kraja epizode
      u uglu se pojavi kartica sa nazivom sledeće i odbrojavanjem; OK ili dodir je pušta odmah, Nazad ili
      „Otkaži” je sklanja. Posle tri epizode zaredom bez ijednog pritiska kartica pita „Još gledate?” i
      sledeća ne kreće sama
- [x] Crna tema (Bane, 18. 11. 2026, treći krug predloga): prekidač u Podešavanjima (telefon i TV),
      podrazumevano isključen. Pozadina je potpuno crna, površine za nijansu tamnije, bez sjaja u uglu
- [x] Nova ikonica i izbor teme (Bane, 19. 11. 2026): ikonica i TV baner su neonska narandžasta kutija sa
      ledeno plavim znakom za puštanje na teget pozadini (predlog 7B). Podešavanja → Izgled → „Tema” (telefon
      i TV, bira se sa spiska): Siva (podrazumevana) i Teget. Neonske teme iz 1.0.11 su uklonjene (Bane);
      ko ih je izabrao dobija istu boju bez neona. Donji meni na telefonu nema podlogu oko ikone izabrane
      kartice. Crna tema ostaje poseban prekidač i radi uz svaku temu
- [x] Četiri nove teme (Bane, 23. 11. 2026): Grafit, Ponoć, Šuma i Bordo, pored Sive (podrazumevana) i
      Teget, na istom spisku „Tema” (telefon i TV). Menjaju samo boju pozadine, površina i mesta bez slike
- [x] Izgled „Staklo” (Bane, 23. 11. 2026, izabrao sa slika; zamenjuje dosadašnji izgled svuda, telefon i
      TV, svi ekrani odjednom): pozadina je tamni preliv sa dva meka svetla, sve pločice su poluprovidne sa
      tankom svetlom ivicom kao meniji u plejeru (bez zamućenja, zbog brzine na TV-u), izabrano je providna
      boja aplikacije sa ivicom u toj boji, fokus na TV-u je okvir u boji sa mekim oreolom, a meni je
      lebdeća kapsula (donji i bočni na telefonu, bočni na TV-u). Zajednički delovi su u
      `ui/common/theme/Glass.kt`; novi ekrani koriste njih i `AppScaffold`, ne svoje boje i okvire
- [x] Pozadina „Stakla” se crta iz jedne male, unapred nacrtane slike umesto tri preliva u svakom kadru
      (23. 11. 2026, merenje na projektoru: listanje se posle „Stakla” osetno usporilo). Izgled je isti
- [x] TV Uživo: kategorija se bira tek na OK, ne dok fokus prolazi preko nje (Bane, 19. 11. 2026)
- [x] Početni prikaz Podešavanja i Uživo (Bane, 19. 11. 2026; telefon i TV): Podešavanja se posle prelaska
      na drugu karticu i povratka otvaraju na celom meniju, ne na poslednjoj grupi. Uživo se pri pokretanju i
      pri svakom ulasku sa druge kartice otvara na „Nedavno”; dok nedavno gledanih nema, na grupi od prošlog
      puta. Filmovi i serije i dalje pamte poslednju kategoriju
- [x] Ručno povezivanje kanala sa programom (Bane, 19. 11. 2026, četvrti krug predloga): za kanale kojima
      se oznaka u listi i u vodiču ne poklapaju. U Vodiču dugi OK na nazivu kanala (TV), odnosno „Poveži
      program” u detaljima kanala bez programa (telefon), nudi kanale vodiča sa najsličnijim nazivom i šta
      je sada na njima. Izbor se pamti po `stableKey` (`epg_links`), ima prednost nad `tvg-id` i preživljava
      osvežavanje liste. Baza v15: `epg_channels` (spisak kanala vodiča) i `epg_links`. Za nepovezane kanale
      vodiča čuva se samo program narednih 8 sati, bez opisa
- [x] Rezervni izvor kanala (Bane, 19. 11. 2026, četvrti krug predloga): kanal sa potpuno istim nazivom
      (Banetov izbor; „HD” i obična verzija ostaju odvojeni) u više spojenih lista prikazuje se jednom u
      „Svi kanali”, pretrazi i Vodiču, iz prve liste. Kad se izvor ne javi ni posle ponovnih pokušaja,
      plejer sam pređe na isti kanal iz sledeće liste i to kratko javi; dugme „Izvori” u plejeru (samo kad
      izvora ima više) bira ručno. Posle dva bežanja zaredom sa iste liste, kanali kreću odmah sa sledećeg
      izvora dok je plejer otvoren. Unutar kategorija se ništa ne menja
- [x] Više lista omiljenih kanala (Bane, 19. 11. 2026, četvrti krug predloga): liste sa imenom („Sport”,
      „Deca”) samo za kanale uživo (Banetov izbor), među kategorijama odmah ispod „Omiljeni”. Dok nijedne
      nema, dodavanje u omiljene radi kao do sada; kad postoji bar jedna, isti potez otvara izbor liste sa
      kvačicama i poljem „Nova lista”. Prva lista se pravi dugim pritiskom (dugo OK na TV-u) na „Omiljeni”,
      a istim potezom na listi se ona preimenuje ili briše. „Omiljeni” ostaju spisak svih omiljenih: kanal
      u nekoj listi je uvek i tamo, brisanje liste ga ne uklanja. Baza v16: `favorite_lists` i
      `favorite_list_items`. Liste se još ne šalju u prenosu sa telefona na TV
- [x] Liste omiljenih pod jednim dugmetom (Bane, 19. 11. 2026): svaka lista je pravila svoje dugme, pa je
      red čipova bio pretrpan. Po Banetovom izboru (predlog A) ostaje jedno dugme „Omiljeni”, a liste su
      red kartica iznad kanala („Omiljeni”, imenovane liste, „+” za novu) na telefonu uspravno i položeno
      i na TV-u (Gore iz kanala, Levo/Desno, OK otvara listu, dugi OK je meni liste). Red postoji tek kad
      postoji bar jedna lista; dugme „Omiljeni” otvara karticu koja je bila otvorena poslednji put. Liste
      su sada nezavisne (Banetov izbor): kanal može da bude samo u „Sport”, uklanjanje iz „Omiljenih” ga ne
      vadi iz lista, a zvezdica svetli ako je u bar jednoj listi. Bez promene šeme baze
- [x] Preuzimanje filmova i epizoda na telefon (Bane, 19. 11. 2026, četvrti krug predloga; samo telefon).
      Dugme „Preuzmi” u detaljima filma i strelica pored svake epizode; spisak je u Podešavanja → „Preuzeto”
      (napredak, veličina, pauza, nastavak, brisanje, zauzeće). Preuzima se jedno po jedno, u folder aplikacije
      u memoriji telefona. Po Banetovom izboru preuzimanje staje dok se nešto gleda (nalog sa jednom vezom) i
      samo se nastavlja kad se plejer zatvori, i ide samo preko Wi-Fi mreže dok se u Podešavanjima ne uključi
      „Preuzimanje preko mobilnih podataka”. Prekinuto preuzimanje se nastavlja tamo gde je stalo. Preuzeto se
      pušta sa uređaja, bez interneta, i iz „Preuzeto” i iz detalja filma ili serije. Baza v17 (`downloads`).
- [x] Snimci i preuzeto u Downloads/Glowbox (Bane, 19. 11. 2026; telefon i TV). Snimci idu u
      `Downloads/Glowbox/Snimci`, a preuzeti filmovi i epizode u `Downloads/Glowbox/Preuzeto`, pod čitljivim
      imenom (naslov i oznaka epizode), pa se vide u svakom upravniku fajlova i ostaju posle deinstalacije.
      Izbor SD kartice ili USB memorije za snimke ostaje kakav je bio. Po Banetovom izboru se pri prvom
      pokretanju prenose i postojeći fajlovi iz foldera aplikacije (snimci samo iz memorije uređaja); što ne
      može da se prenese ostaje na starom mestu i dalje radi. Od Androida 11 ne treba dozvola; na Androidu 10
      i starijim se traži dozvola za memoriju, a bez nje se i dalje koristi folder aplikacije.
- [x] Dugmad daljinskog po izboru (Bane, 19. 11. 2026, četvrti krug predloga; TV, plejer kanala). Podešavanja →
      „Dugmad daljinskog”: za crveno, zeleno, žuto i plavo dugme, kanal gore i dole, meni i dugo OK bira se
      radnja (prethodno gledani kanal, sledeći ili prethodni kanal, spisak kanala, program, omiljeni, zvuk,
      titlovi, brza podešavanja, informacije o strimu, kontrole, ništa). Podrazumevano po Banetovom izboru:
      crveno = prethodno gledani kanal, zeleno = program, žuto = omiljeni, plavo = zvuk, CH± = sledeći i
      prethodni kanal, meni = brza podešavanja, dugo OK = spisak kanala. Plejer filmova i serija se ne menja.
- [x] Ekran pauze sa opisom (Bane, 19. 11. 2026, četvrti krug predloga; telefon i TV, filmovi, serije i
      kanali uživo, Banetov izbor): posle 5 sekundi pauze kontrole ustupe mesto naslovu, oznakama (ocena,
      godina, žanr), opisu, tome koliko je ostalo do kraja i satu; za kanal emisija iz vodiča sa opisom i
      šta sledi. Prvi taster ili dodir samo vraća kontrole, dugme za puštanje nastavlja odmah. Posle
      minut pauze i dalje sledi zatamnjenje, a posle 30 minuta izlaz. Gasi se u Podešavanjima („Ekran pauze”)
- [x] Pauza bez zatamnjenja (Bane, 21. 11. 2026): ekran pauze se pojavljuje posle 10 sekundi (bilo 5) i
      ostaje dok se nešto ne pritisne; zatamnjenje posle minut pauze je uklonjeno. Izlaz posle 30 minuta
      ostaje (Banetov izbor). Važi svuda: TV i telefon, filmovi, serije, kanali, arhiva i snimci.
      Kad je „Ekran pauze” isključen u Podešavanjima, slika se posle istih 10 sekundi samo zatamni, bez teksta.

- [x] Brže kretanje na TV-u (Bane, 19. 11. 2026, pregled „Uskladi izgled cele aplikacije”, stavke P). Bez
      promene izgleda: u Uživo i u Filmovima/Serijama korak D-pada više ne crta iznova ceo ekran i sve redove
      (samo stari i novi red fokusa); vodič se više ne crta ceo na svaki korak (vreme se osvežava na 30 s,
      izabranu emisiju čita samo donji panel); pregled kanala ne trepće (program prethodnog kanala ostaje
      prigušen dok ne stigne novi) i slika prethodnog kanala staje čim fokus krene; sjaj pozadine se crta
      jednom; bočni meni bez senke
- [x] Brže prvo otvaranje Filmova i Serija (Bane, 19. 11. 2026; telefon i TV)
  - [x] Spiskovi kategorija, filmova i serija se čuvaju na uređaju (Podešavanja → Sadržaj → „Čuvaj filmove i
        serije na uređaju”, uključeno). Sačuvani spisak se prikazuje odmah; kad se lista osveži (dnevno ili
        ručno), noviji se preuzima u pozadini i tiho zameni prikaz. Isključivanje briše sačuvano
  - [x] Novi izgled dok se učitava (Bane izabrao C, svuda; predlozi u `dizajn/ucitavanje/`): umesto sivih obrisa
        krug u akcentnoj boji i poruka „Učitavam filmove…”/„Učitavam serije…” u sredini, na telefonu (uspravno
        i položeno) i TV-u; pri prvom otvaranju sekcije i rečenica da prvo učitavanje može da potraje
- [x] Usklađivanje izgleda (isti pregled, stavke V1 do V6 i F1 do F4, slike u `dizajn/celina/`)
  - [x] V1 Jedan izgled redova na TV-u: svi spiskovi (Podešavanja, pretraga, snimci, arhiva, epizode,
        izbori u plejeru, dijalozi) su pločice kao kategorije, razmak 6 dp; bočni meni počinje u visini naslova
  - [x] V2 Teget tema do kraja: podloga postera i slika epizoda, prelaz iza naziva bez postera, obrisi pri
        učitavanju i inicijali kanala bez logoa prate temu (`PlaceholderColors`); Siva i crna tema bez promene
  - [x] V3 Uživo na TV-u: levi panel kao kod filmova i serija (pretraga, Omiljeni, Nedavno i Svi kanali stoje
        zakačeni, ispod polje „Traži kategoriju” i kategorije koje se skroluju)
  - [x] V4 Filmovi i serije na telefonu (uspravno): isti red čipova kao u Uživo (zvezdica Omiljeni,
        „Nastavi gledanje”, kategorija), ispod naslov sa brojem stavki
  - [x] V5 Pretraga izgleda kao ostatak aplikacije: kanali kao pločice iz Uživo, filmovi i serije kao
        red postera, naslovi grupa sa brojem pogodaka (telefon i TV)
  - [x] V6 Sat na svim TV ekranima: pretraga, Više kanala, snimci i arhiva imaju isti naslov sa satom
        (ekrani sa formularima bez sata)
  - [x] F4 Obrisi pri učitavanju kanala na telefonu imaju oblik pločice kanala
  - F1 do F3 ostaju kako jesu (Bane): bez Početne na telefonu, isti redosled kartica, grupa
    Podešavanja na TV-u prati fokus
- [x] Bočni meni na TV-u: strelica na vrhu prebacuje na samo ikonice i nazad; raširen meni pomera sadržaj
      udesno (bez preslaganja) umesto da ga pokrije. Izbor se pamti, prvi put je meni pun (Bane)
- [x] Ceo ekran na telefonu: gornja i donja sistemska traka sakrivene na svim ekranima, uspravno i
      položeno; prevlačenje od ivice ih privremeno pokaže. Opcija „Ceo ekran” u Podešavanjima > Izgled,
      uključena podrazumevano (Bane)
  - [x] Bez gornje trake sadržaj se penje bliže vrhu: počinje malo ispod donje ivice kamere, da kamera
        ne preklopi gornji red (npr. polje za pretragu) (Bane)
- [x] Vodič na TV-u: desno na poslednjoj emisiji pomera vreme, a fokus ostaje desno na istoj emisiji
      (ranije je skakao na naziv kanala levo)
- [x] Pretraga i Vodič na TV-u su kartice sa bočnim menijem kao ostale (Bane); levo sa naziva kanala u
      Vodiču vraća vreme do sadašnjeg, pa ide u meni (ranije emisije dugmetom „Ranije”)
- [x] TV, Uživo, Filmovi i Serije: drugi OK na već izabranoj kategoriji (i Omiljeni, Nedavno, Svi, Nastavi
      gledanje) prelazi na njene stavke, kao Desno (Bane)
- [x] Telefon: prelaz između ekrana je kratko pretapanje od 150 ms (Bane, 20. 11. 2026) umesto podrazumevanog
      od 700 ms; TV ostaje bez prelaza
- [x] Roditeljska zaštita, kanali (Bane, 20. 11. 2026; telefon i TV): kanali zaključane kategorije se ne vide
      nigde (kategorija, Svi, Omiljeni, liste omiljenih, Nedavno, pretraga, Početna, broj kanala) dok se ne
      unese PIN. Kategorija ostaje u spisku sa katancem; dodir ili OK na njoj traži PIN, pa je otvara. Snimak
      kanala iz zaključane kategorije traži PIN pre puštanja. Baza v18: kolona `categories.locked`, koju
      `LockRepository` usklađuje sa podešavanjima
- [x] Izveštaj o grešci (Bane, 20. 11. 2026; telefon i TV): kad aplikacija padne, zapis ostaje samo na uređaju
      (`data/crash/CrashLog.kt`), bez adresa (pa i bez korisničkog imena i lozinke iz adrese strima). Pri sledećem
      pokretanju aplikacija jednom nudi izveštaj, a stavka ostaje u Podešavanja → O aplikaciji. Telefon ga deli
      kroz sistemsko „Podeli”, TV ga čuva kao fajl u Downloads/Glowbox. Ništa se ne šalje samo od sebe
- [x] Prenos sa telefona na TV je potpun (Bane, 20. 11. 2026): nosi i imenovane liste omiljenih (lista sa istim
      nazivom se na TV-u dopunjuje), to koje su liste spojene i sakrivene kanale
- [x] Sakrivanje pojedinačnog kanala (Bane, 20. 11. 2026; telefon i TV): u Uživo dugi pritisak na kanal (dugi OK
      na TV-u) otvara meni sa stavkom „Sakrij kanal”; na TV-u je to isti meni kao za omiljene. Sakriven kanal se
      ne vidi nigde (kategorija, Svi, Omiljeni, pretraga, vodič, prebacivanje kanala), a vraća se iz Podešavanja,
      stavka „Sakriveni kanali”. Baza v19: tabela `hidden_channels`, po stalnom ključu kanala
- [x] TV, Uživo: kad se kanal izvadi iz otvorene liste omiljenih, fokus ostaje među kanalima (Bane, 20. 11.
      2026): prelazi na sledeći kanal ispod, za poslednji u listi na onaj iznad, a kad lista ostane prazna na red
      „Omiljeni”. Ranije je fokus odlazio na bočni meni
- [x] TV, Filmovi i Serije: dugi OK na posteru u grupi „Favoriti” uklanja film ili seriju iz favorita (Bane,
      20. 11. 2026); fokus prelazi na sledeći poster, a kad je poslednji, na prethodni ili na red „Favoriti”.
      Ranije je dugi OK radio samo u kategorijama i u pretrazi
- [x] TV: dugi OK dodaje u favorite i uklanja iz njih na svakom posteru filma ili serije (Bane, 20. 11. 2026):
      i na „Početnoj” (nedavno dodati filmovi i serije) i u zajedničkoj pretrazi, sa zvezdicom na posteru. Red
      „Nastavi gledanje” zadržava svoj meni na dugi OK
- [x] TV: dugi OK na filmu ili seriji koja je već u favoritima prvo pita „Ukloni iz favorita” (Bane, 20. 11.
      2026), sa „Ukloni” kao prvom stavkom i „Otkaži” kao drugom; dodavanje ide odmah. Jedno držanje tastera OK važi kao jedan dugi pritisak, pa držanje više ne
      uklanja i susedne postere
- [x] Preuzete epizode zaredom bez interneta (Bane, 20. 11. 2026; telefon): kad spisak epizoda ne može da se
      pročita sa servera, „Sledeća” i kartica na kraju epizode idu kroz preuzete epizode te serije. Ništa se ne
      preuzima samo od sebe
- [x] Glasovna pretraga na TV-u (Bane, 20. 11. 2026; samo zajednička pretraga): dugme „Glasom” pored polja
      otvara sistemsko prepoznavanje govora, pa odmah traži izgovoreno. Aplikacija ne snima i ne traži dozvolu
      za mikrofon; na uređaju bez prepoznavanja govora dugmeta nema
- [x] Nedavne pretrage (Bane, 20. 11. 2026; zajednička pretraga, telefon i TV): dok je polje prazno vidi se
      poslednjih 8 upita, izbor odmah traži, uz „Obriši”. Upit se pamti kad se potvrdi na tastaturi, izgovori
      ili kad se otvori neki rezultat. Čuva se samo na uređaju
- [x] Brži start i prvo listanje (Bane, 20. 11. 2026): uz profile biblioteka, koje je APK već nosio, dodat je
      profil za kod same aplikacije (`app/src/main/baseline-prof.txt`, jedno pravilo za ceo paket). Android ga
      prevodi u pozadini dok uređaj miruje, pa se razlika vidi tek posle toga. Snimanje profila na emulatoru u
      CI nije rađeno (produžava svaki build)
- [x] Telefon nalazi TV i bez oglašavanja u mreži (Bane, 20. 11. 2026; prenos na TV i slanje na TV): TV sluša
      na stalnom portu kad je slobodan, a telefon uz mDNS pita i adrese u svom delu kućne mreže (`LanScan`).
      Radi kad su i telefon i TV na ovoj verziji
- [x] Meni posle dugog OK na TV-u ne bira prvu stavku kad se taster pusti (OK važi tek od novog pritiska)
- [x] Pretraga u Uživo traži i po emisijama iz vodiča (Bane, 20. 11. 2026; TV i telefon): ispod kanala je
      grupa „U TV vodiču” sa emisijama koje traju ili tek počinju. Emisija koja traje pušta kanal, a buduća
      dobija podsetnik, kao u zajedničkoj pretrazi
- [x] Prenos sa telefona na TV ne čeka učitavanje lista (Bane, 20. 11. 2026): telefon je i do sada slao samo
      adrese i naloge, ali je TV odgovarao tek kad preuzme svaku listu (minuti). Sada TV odmah potvrdi prijem
      i primeni sve što ne traži preuzimanje, a nove liste učitava sam u pozadini; napredak i ishod pokazuje
      na svom ekranu. Važi i za prenos iz Podešavanja (izbor TV-a sa spiska) i za put preko QR koda
- [x] Prenos na TV: ponovljeno slanje ne čeka prethodno učitavanje (Bane, 20. 11. 2026): TV potvrdi prijem
      pre bilo kakvog upisa i sve radi sam u pozadini (baza ume da bude zauzeta dok TV osvežava liste), pa
      telefon ne čeka ni tada ni dok se učitava prethodni paket; ishod se vidi samo na TV-u
- [x] Brža „Početna” na TV-u (Bane, 20. 11. 2026): redovi „nedavno dodato” se čuvaju na uređaju (ista opcija
      „Čuvaj filmove i serije na uređaju”) i prikazuju odmah po pokretanju, a ceo spisak liste se čita u
      pozadini i redovi se tiho zamene. Sačuvan red se ne koristi ako su se promenile sakrivene ili zaključane
      kategorije. Pri prvom preuzimanju stoji poruka „Učitavam filmove…”, kao u Filmovima i Serijama
- [x] TV „Početna”: Nazad vraća fokus na otvorenu stavku (Bane, 21. 11. 2026): posle povratka iz detalja
      filma ili serije, ili iz plejera, fokus je opet na istom posteru ili kanalu, a ne u bočnom meniju.
- [x] TV: fokus se posle Nazad vraća na pravo mesto (Bane, 21. 11. 2026, pregled kretanja kroz sve TV ekrane):
      Pretraga (opšta, kanala, filmova i serija) vraća na otvoreni rezultat, a ne u polje za kucanje; Serija vraća
      na puštenu epizodu, a Levo sa epizoda ide na izabranu sezonu; Vodič vraća na emisiju na kojoj je fokus bio;
      Podešavanja na red sa kog je otvoren podekran; Liste na listu koja je menjana; u detaljima filma fokus više
      ne skače na „Pusti” ako je korisnik već na drugom dugmetu
- [x] TV: kraći put kroz menije (Bane, 21. 11. 2026): OK na sekciji u bočnom meniju svuda prebacuje fokus u
      sadržaj (i na „Početnoj”); u Filmovima i Serijama Desno sa kategorije ide na prvi poster na ekranu; Levo sa
      postera i kanala vraća na izabranu kategoriju; Uživo posle plejera fokusira kanal koji je gledan poslednji;
      Vodič se otvara sa fokusom na emisiji koja traje na prvom kanalu
- [x] TV Liste i Podešavanja (Bane, 21. 11. 2026): „Liste” se otvaraju sa fokusom na aktivnoj listi, a dugi OK na
      listi otvara opcije (osveži, izmeni, spoji, obriši), uz kratko uputstvo iznad spiska; u Podešavanjima se
      jezik, boja i veličina prikaza biraju sa spiska vrednosti umesto kruženjem na svaki OK
- [x] TV: bočni meni ne treperi pri povratku (Bane, 21. 11. 2026): dok fokus putuje u sadržaj (Nazad iz druge
      sekcije na „Početnu”, povratak iz detalja ili plejera, pokretanje) meni se ne širi i ne boji stavku
- [x] TV: čuvar ekrana (Bane, 22. 11. 2026): kad se izabrano vreme ne pritisne nijedan taster, preko svega
      se vidi crn ekran sa satom i datumom koji se polako sele; bilo koji taster ga gasi i fokus ostaje gde je
      bio. Podešavanja > Izgled „Čuvar ekrana”: Isključeno, 2, 5 (podrazumevano), 10 ili 15 minuta. Ne pali
      se preko plejera (pauza ima svoj ekran) ni dok se čeka telefon. Samo TV; ekran se ne drži upaljenim.
- [x] Pretraga oprašta promašeno slovo (Bane, 22. 11. 2026; telefon i TV): zajednička pretraga i pretraga
      kanala, filmova i serija nalaze naziv i kad je u upitu jedno slovo pogrešno, ispušteno ili suvišno (upit
      od bar 5 slova) i kad su izostavljene kvačice. Tačni pogoci idu prvi. Emisije iz vodiča se i dalje traže tačno
- [x] Info traka kanala (Bane, 22. 11. 2026; plejer uživo, telefon i TV): ispod trake emisije piše koliko je
      minuta prošlo i koliko još traje, a red „Sledeće” je ispod toga
- [x] Arhiva pravo iz Vodiča (Bane, 22. 11. 2026; telefon i TV): završena emisija kanala sa arhivom nudi „Pusti
      emisiju” (pušta baš nju) i „Gledaj uživo”; spisak arhive ostaje kao treća mogućnost. Kod Xtream lista se
      emisija iz vodiča pronalazi u spisku arhive sa servera po terminu. Panel „Program” u plejeru prošle emisije
      ne prikazuje, pa tamo ostaje red „Arhiva”
- [x] Prekidač „Automatski pusti sledeću epizodu” (Bane, 22. 11. 2026; telefon i TV, podrazumevano uključen):
      kad je isključen, kartica na kraju epizode i dalje nudi sledeću, ali ona ne kreće sama
- [x] Pravila za kanale (Bane, 22. 11. 2026; telefon i TV, Podešavanja > Sadržaj, sva tri isključena dok se ne
      uključe): „Sakrij SD kad postoji HD” (kanal sa oznakom SD ili bez oznake, kad isti u listi postoji u HD,
      FHD ili 4K), „Sakrij kanale +1” i „Spoji istoimene kanale” (isti naziv u jednoj listi prikazuje se jednom).
      Sve se čita iz naziva kanala (`domain/ChannelRules.kt`); omiljeni kanali se ne sklanjaju. Baza v22, kolona
      `channels.ruleHidden`, održava je `ChannelRulesSync` posle promene pravila i posle osvežavanja liste
- [x] TV Filmovi i Serije: šira mreža postera (Bane, 22. 11. 2026): čim fokus pređe na postere (OK na
      kategoriji ili Desno), kategorije se skupe u tanku traku sa ikonicama i u red staje 7 malo manjih
      postera umesto 5 (Bane izabrao sa slike). Levo sa prve kolone ili Nazad ponovo otvara kategorije, sa
      fokusom na izabranoj. OK na kategoriji je bira i prelazi na postere čim stignu. Uživo i telefon se
      ne menjaju.
- [x] Filteri (Bane, 22. 11. 2026; telefon i TV, izgled izabran sa slika): Filmovi i Serije = godina
      (2020. i novije, 2010. i novije, 2000. i novije, starije), najmanja ocena (6+, 7+, 8+) i „Samo 4K” (po nazivu);
      Uživo = kvalitet (HD i bolje, FHD i bolje, 4K) i zemlja, oboje iz naziva kanala i kategorije. TV: red
      „Filter” u levom panelu otvara mali prozor, vrednosti se menjaju sa Levo/Desno; telefon: ikonica filtera sa
      brojem uslova i list odozdo. Filter se pamti (posebno filmovi, serije, kanali). Filter kanala koristi kolonu
      `channels.ruleHidden` (`ChannelRulesSync`), pa važi u svim spiskovima kanala, i za omiljene
- [x] Filter kanala: izbor zemlje iz spiska (Bane, 22. 11. 2026, posle probe: previše dugmića; telefon i TV):
      red „Zemlja” otvara abecedni spisak sa pretragom i punim nazivima („Srbija (RS)”). Nude se samo prave
      zemlje (ISO 3166, i oznake sa tri slova i uobičajene poput UK, SR), nazivi paketa i liga se ne računaju
- [x] Brži filter kanala (Bane, 22. 11. 2026: na TV-u su kanali stizali tek posle 15 s): kvalitet i zemlja se
      čuvaju u bazi (v23: `channels.quality`, `country`, `filterHidden`), popune se jednom posle uvoza liste,
      a promena filtera je jedna naredba nad bazom umesto čitanja naziva svih kanala. Pravila za kanale i dalje
      koriste `ruleHidden` i računaju se odvojeno
- [x] Filter kanala: prazne kategorije se ne prikazuju (Bane, 23. 11. 2026): dok je uključen filter ili neko
      pravilo za kanale, kategorije bez ijednog preostalog kanala nestaju iz spiska (telefon, TV, više kanala)
- [x] Filter kanala ostaje uključen i tokom osvežavanja liste (Bane, 23. 11. 2026: filter se sam gasio pa palio):
      kanali se pri uvozu upisuju sa već izračunatim kvalitetom, zemljom i stanjem filtera (`ChannelWriter`)
- [x] TV Početna: veliki prikaz stavke u fokusu (Bane, 22. 11. 2026; samo TV, izgled izabran sa slike): iznad
      redova stoje naziv, godina, ocena, žanr i opis filma ili serije sa slikom na desnoj strani; za kanal logo i
      emisija koja traje. Naziv, godina i ocena stižu odmah iz spiska, a opis i široka slika se čitaju sa servera
      kad fokus zastane 0,35 s i pamte se dok je ekran otvoren (`TvStartViewModel.preview`)
- [x] Telefon uspravno, doterivanje (Bane, 22. 11. 2026; sve izabrano sa slika u `dizajn/uspravno`):
  - plejer: donje kontrole stoje odmah ispod slike umesto na dnu ekrana
  - okretanje: dugme u plejeru (u uglu slike uspravno, u gornjem redu položeno) okreće ekran i kad je
    rotacija na telefonu zaključana; poruka o položenom prikazu samo pri prvom puštanju; Podešavanja >
    Izgled > „Položaj ekrana” (Automatski, Uvek uspravno, Uvek položeno) za celu aplikaciju
  - Filmovi i Serije: jedno široko dugme kategorije sa brojem naslova umesto duplog naslova, naziv
    postera u jednom redu, zvezdica samo na omiljenima (dodavanje dugim pritiskom); posteri isto i položeno
  - Uživo: svi redovi iste visine, broj kanala na čipu izabrane grupe; izabran čip u boji aplikacije
  - vrh (naslov i čipovi) se sklanja dok se lista nadole, u Uživo, Filmovima i Serijama
  - TV vodič uspravno je spisak „sada i sledeće”; položeno ostaje mreža
- [x] Približno premotavanje fajlova bez indeksa (Bane, 22. 11. 2026; telefon i TV): matroska fajl bez
      indeksa se više ne vodi kao nepremotljiv. Mesto se procenjuje iz veličine fajla i trajanja, posle
      skoka se nađe početak bloka i po potrebi skoči još jednom, bliže. Fajl bez trajanja postaje
      premotljiv posle dvadesetak sekundi učitavanja. Nastavak gledanja, promena dekodera i brzine više
      ne vraćaju takav fajl na početak. Ako server ne da fajl od sredine, ostaje poruka kao do sada.

## 7. Testiranje

- Unit testovi za parsere (M3U, XMLTV) i Xtream modele se pokreću u CI-ju na svaki push.
- Test podaci su **izmišljeni** primeri. Pravi linkovi, liste i lozinke ne ulaze u repo.
- Ručna provera posle svake faze na telefonu i na TV-u, po spisku „Provera” iz faze.

## 8. Poznati rizici

- **Nema lokalnog builda**: greške u kompilaciji se vide tek u CI-ju. Zato male izmene i čest push.
- **Raznolikost strimova**: provajderi koriste različite formate i kodeke; neki strimovi
  neće raditi bez softverskog dekodera. Predvideti izbor dekodera u podešavanjima.
- **Velike liste**: 50.000+ stavki. Parser i liste na ekranu moraju raditi strimovano i sa straničenjem.
- **Fokus na TV-u**: najčešći izvor grešaka u Compose for TV. Zato posebna Faza 6.
