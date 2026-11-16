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
| Repo | Privatan, `mcbane23/iptv-player` |
| Način rada | Claude Code na webu i u Android aplikaciji |
| Package name (applicationId) | `com.bane23.iptvplayer` (potvrđeno) |
| Jezici interfejsa | Engleski i srpski (svi tekstovi u `strings.xml`) |
| Tema | Tamna, sa jednom akcentnom bojom |

Verzije biblioteka se ne zakucavaju u ovom planu. U Fazi 0 uzeti najnovije **stabilne**
verzije i upisati ih u version catalog.

Predlog SDK nivoa: `minSdk 24`, `targetSdk` i `compileSdk` na najnovijem stabilnom.

## 3. Otvorene odluke (pitati vlasnika pre nego što zatrebaju)

- [ ] **Ime aplikacije.** Za sada ostaje radni naziv „IPTV Player”. Konačno ime potvrditi pre Faze 8.
- [ ] **Akcentna boja i ikonica.** Tema je potvrđena (vidi odeljak 2), boja se bira kasnije.
      Do tada koristiti jednu privremenu akcentnu boju definisanu na jednom mestu u temi.
- [ ] Da li je potrebna distribucija van GitHub-a (npr. Google Play). Utiče na Fazu 8.
- [ ] **Sinhronizacija preko naloga (Faza 9).** Gde se čuvaju podaci (npr. korisnikov
      Google Drive, sopstveni server ili nešto treće), koji nalog (Google, email) i šta se
      sinhronizuje. Mora ostati besplatno i bez praćenja, a lozinke lista ne smeju na tuđi server.

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

- **Debug APK**: checkout, `actions/setup-java` (Temurin 21), `gradle/actions/setup-gradle`
  (keširanje), `./gradlew assembleDebug`, na main-u upload APK-a kao artifact i objava pre-release-a
- **Testovi i lint**: isti koraci za pripremu, `./gradlew testDebugUnitTest lintDebug`,
  upload izveštaja

CI je zelen tek kad oba posla prođu. Pre-release se objavljuje čim je APK gotov, ne čeka testove.

APK artifact se čuva samo za build-ove sa `main`-a. Push na `main` dodatno
objavljuje APK kao pre-release `dev-latest` (stalan link, zgodno za TV). Grane ne objavljuju APK
(odluka vlasnika 2026-11-16, linkovi po grani su bili zbunjujući); na njima build i testovi
služe samo kao provera. Ažuriranje iz aplikacije prati samo `main`. Za sada je APK
**potpisan debug ključem**.

**Ažuriranje iz aplikacije (urađeno, dev kanal):** kod ostaje privatan, a CI APK sa `main`-a objavljuje i u
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
- [x] Telefon: rotacija, gestovi za jačinu zvuka i osvetljenje, slika u slici (PiP)
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
- [ ] Catch-up / arhiva (M3U `catchup` atributi i Xtream `timeshift`)
- [ ] Roditeljska zaštita PIN-om (zaključavanje kategorija)
- [ ] Više profila
- [x] Dodavanje liste na TV preko telefona: TV prikaže QR kod, telefon u istoj mreži otvori stranicu sa TV-a
      i pošalje podatke liste (urađeno uz Fazu 6, čeka probu)
- [x] Izvoz i uvoz podešavanja i lista (prebacivanje sa telefona na TV): sa stranice koju otvara QR kod
  aplikacija na telefonu šalje sve liste, omiljene kanale, uređenje kategorija i podešavanja pravo na TV
- [ ] Prikaz više kanala odjednom (multi-view)
- [ ] Automatsko pokretanje pri paljenju uređaja (TV box)

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

### Faza 8: Izdanje

- [ ] Potvrđeni ime, akcentna boja, ikonica i TV baner
- [ ] Keystore napravljen, secrets dodati, `release.yml` radi
- [ ] R8/ProGuard pravila proverena na release buildu
- [ ] Verzionisanje (`versionCode` iz broja CI run-a ili iz taga)
- [ ] README: kako instalirati APK na telefon i na TV
- [ ] Prvo izdanje `v1.0.0` u GitHub Releases
- [ ] Ažuriranje iz aplikacije prelazi sa `iptv-player-dev` na zvanična izdanja u javnom repou
  (`UpdateSource.kt` i `release.yml`)

### Faza 9: Naknadne ideje

Ideje dodate posle prvobitnog plana. Rade se posle Faze 8, redosled po želji vlasnika.
Pre početka svake stavke dogovoriti detalje (vidi odeljak 3).

**Sinhronizacija gledanog sadržaja preko naloga**

- [ ] Prijava nalogom (Google ili email) u podešavanjima, potpuno opciono
- [ ] Sinhronizacija pozicije gledanja: film ili serija započeti na TV-u nastavljaju se
      na telefonu od istog mesta, i obrnuto
- [ ] Odlučiti šta se još sinhronizuje (favoriti, istorija, liste) i gde se podaci čuvaju

**Premotavanje strima**

- [ ] Dugmad napred/nazad skaču po 10 s (podrazumevano), vrednost skoka podesiva u podešavanjima
- [ ] Brzo premotavanje na TV-u: držanje D-pad levo/desno ubrzava premotavanje,
      sa pregledom vremena na traci, da se lako stigne do željenog mesta

**Snimanje sadržaja**

- [ ] Snimanje kanala koji se trenutno gleda (dugme u plejeru), snimak se čuva na uređaju
- [ ] Zakazano snimanje iz TV vodiča (EPG) ili ručno (kanal, početak, kraj), preko WorkManager-a
- [ ] Ekran „Snimci”: puštanje, brisanje, prikaz zauzetog prostora
- [ ] Podešavanje mesta čuvanja (interna memorija, USB/SD) i upozorenje kad ponestaje prostora

**Izlaz iz aplikacije**

- [ ] Izlaz samo na dupli pritisak „Nazad” sa početnog ekrana: prvi pritisak prikazuje
      toast poruku („Pritisnite Nazad još jednom za izlaz”), drugi u roku od ~2 s zatvara aplikaciju.
      Radi isto na telefonu i na TV-u (daljinski)

**Titlovi**

- [ ] Izgled titlova u podešavanjima: uključivanje/isključivanje crne pozadine,
      veličina slova i položaj na ekranu (visina od dna), sa pregledom

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
