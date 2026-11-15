# Plan projekta: IPTV plejer za Android telefon i Android TV

Stanje: **planiranje završeno, kod još nije započet.** Sledeći korak je Faza 0.

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
| Build | Gradle (Kotlin DSL) + version catalog (`gradle/libs.versions.toml`) |
| Isporuka | Jedan APK za telefon i TV |
| Gde se bilduje | **Samo GitHub Actions** (vidi odeljak 5) |
| Repo | Privatan, `mcbane23/iptv-player` |
| Način rada | Claude Code na webu i u Android aplikaciji |

Verzije biblioteka se ne zakucavaju u ovom planu. U Fazi 0 uzeti najnovije **stabilne**
verzije i upisati ih u version catalog.

Predlog SDK nivoa: `minSdk 24`, `targetSdk` i `compileSdk` na najnovijem stabilnom.

## 3. Otvorene odluke (pitati vlasnika pre nego što zatrebaju)

- [ ] **Ime aplikacije.** Radni naziv je „IPTV Player”.
- [ ] **Package name** (applicationId). Radni predlog: `com.mcbane.iptvplayer`.
      Mora se potvrditi **pre prvog release-a**, jer se posle ne menja bez gubitka podataka.
- [ ] **Boje i ikonica.** Podrazumevano: tamna tema, jedna akcentna boja.
- [ ] **Jezici interfejsa.** Predlog: srpski i engleski od početka (svi tekstovi u `strings.xml`).
- [ ] Da li je potrebna distribucija van GitHub-a (npr. Google Play). Utiče na Fazu 8.

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
- `vod_items`, `series`, `episodes`: za filmove i serije (Faza 5)
- `favorites`: playlistId, tip, itemId, redosled
- `history`: itemId, tip, poslednja pozicija, vreme gledanja
- `epg_programs`: tvgId, početak, kraj, naslov, opis (indeks po tvgId + početak)

Lozinke Xtream naloga čuvati šifrovano (Android Keystore), ne kao običan tekst.

## 5. Build i CI (GitHub Actions)

U cloud okruženju Claude Code-a pristup Google i Maven serverima može biti blokiran,
pa lokalni Gradle build ne radi. **Svaka provera builda ide preko GitHub Actions.**

**`.github/workflows/build.yml`** (pokreće se na svaki push i pull request):

1. checkout
2. `actions/setup-java` (Temurin, JDK 17 ili noviji, prema zahtevu Android Gradle plugina)
3. `gradle/actions/setup-gradle` (keširanje)
4. `./gradlew testDebugUnitTest lintDebug assembleDebug`
5. upload debug APK-a kao artifact

**`.github/workflows/release.yml`** (pokreće se na tag `v*`):

1. isti koraci za pripremu
2. dekodiranje keystore-a iz secreta
3. `./gradlew assembleRelease`
4. objava potpisanog APK-a u GitHub Releases

**GitHub Secrets za release** (dodaje vlasnik repoa, uputstvo ide u README u Fazi 8):

- `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`

Keystore fajl se **nikada** ne komituje u repo. Jednom napravljen ključ mora da se
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

- [ ] Gradle projekat (Kotlin DSL, version catalog, wrapper)
- [ ] `app` modul sa Compose-om, Hilt-om i praznom navigacijom
- [ ] Manifest za telefon + TV (oba launcher-a, TV baner)
- [ ] Tema (tamna), `strings.xml` za srpski i engleski
- [ ] `build.yml` workflow, debug APK kao artifact
- [ ] `.gitignore`, ažuriran README sa uputstvom za preuzimanje APK-a
- [ ] **Provera:** CI zelen, APK instaliran na telefon i TV, aplikacija vidljiva u oba launcher-a

### Faza 1: Liste (playlists)

Cilj: korisnik može da doda, osveži i obriše listu, a sadržaj se upisuje u bazu.

- [ ] Room baza: `playlists`, `categories`, `channels`
- [ ] **M3U parser**: `#EXTM3U`, `#EXTINF` sa atributima `tvg-id`, `tvg-name`,
      `tvg-logo`, `group-title`, `tvg-chno`, `catchup*`; `url-tvg` / `x-tvg-url` iz zaglavlja;
      `#EXTVLCOPT` i `#EXTGRP`; tolerancija na loše formirane redove
- [ ] Parser radi strimovano (liste mogu imati desetine hiljada stavki), upis u bazu u paketima
- [ ] **Xtream Codes klijent** (`player_api.php`): prijava i podaci o nalogu,
      `get_live_categories`, `get_live_streams`; sastavljanje URL-a strima
- [ ] Ekran „Dodaj listu”: M3U link, M3U fajl sa uređaja, Xtream prijava
- [ ] Ekran sa spiskom lista: osveži, izmeni, obriši, izaberi aktivnu
- [ ] Prikaz grešaka razumljiv korisniku (pogrešan link, istekao nalog, nema mreže)
- [ ] Unit testovi za M3U parser i Xtream modele (sa izmišljenim primerima, bez pravih lista)

### Faza 2: Live TV i plejer

Cilj: izbor kategorije, izbor kanala, gledanje.

- [ ] Ekran kategorija i kanala (telefon: lista; TV: kolone sa fokusom)
- [ ] Media3 plejer: HLS, MPEG-TS, DASH, progresivni strim
- [ ] Kontrole: pauza, sledeći/prethodni kanal, lista kanala preko slike
- [ ] Izbor audio zapisa i titlova, odnos stranica (fit / fill / zoom)
- [ ] Automatski ponovni pokušaj kad strim pukne, jasna poruka kad ne može da se pusti
- [ ] Podešavanje User-Agent-a po listi (neki provajderi ga zahtevaju)
- [ ] Ekran ostaje upaljen tokom gledanja; pravilno ponašanje pri izlasku i povratku
- [ ] Telefon: rotacija, gestovi za jačinu zvuka i osvetljenje, slika u slici (PiP)
- [ ] TV: D-pad gore/dole menja kanal, OK otvara listu, brojevi biraju kanal

### Faza 3: Favoriti, istorija, pretraga

- [ ] Dodavanje i uklanjanje favorita, posebna kategorija „Favoriti”
- [ ] „Nedavno gledano” i pokretanje poslednjeg kanala pri startu (opciono u podešavanjima)
- [ ] Pretraga kanala po nazivu kroz sve kategorije
- [ ] Sakrivanje i promena redosleda kategorija

### Faza 4: TV vodič (EPG)

- [ ] XMLTV parser (strimovan, podrška za `.gz`), upis u `epg_programs`
- [ ] Izvor: EPG link iz liste, ručno unet link, ili Xtream `xmltv.php`
- [ ] Povezivanje programa sa kanalima preko `tvg-id` (uz rezervno poklapanje po nazivu)
- [ ] „Sada / Sledeće” u listi kanala i u plejeru
- [ ] Ekran vodiča u obliku mreže (vremenska osa), prilagođen i telefonu i TV-u
- [ ] Periodično osvežavanje preko WorkManager-a, brisanje starih programa
- [ ] Vremenski pomak (time shift) za EPG po listi

### Faza 5: Filmovi i serije (samo Xtream)

- [ ] `get_vod_categories`, `get_vod_streams`, `get_vod_info`
- [ ] `get_series_categories`, `get_series`, `get_series_info` (sezone i epizode)
- [ ] Ekrani sa posterima, detalji, puštanje
- [ ] Nastavak gledanja od zapamćene pozicije, sledeća epizoda

### Faza 6: Doterivanje TV interfejsa

- [ ] Pregled svih ekrana samo sa daljinskim: fokus nikad ne sme da se izgubi
- [ ] Brzina listanja dugih lista, učitavanje logotipa bez zastajkivanja
- [ ] Margine za overscan, čitljivost sa daljine
- [ ] Proba na pravom Android TV uređaju i na slabijem TV box-u

### Faza 7: Podešavanja i dodatne funkcije

Redosled unutar faze po želji vlasnika.

- [ ] Podešavanja plejera (bafer, dekoder hardverski/softverski, podrazumevani jezik zvuka i titlova)
- [ ] Catch-up / arhiva (M3U `catchup` atributi i Xtream `timeshift`)
- [ ] Roditeljska zaštita PIN-om (zaključavanje kategorija)
- [ ] Više profila
- [ ] Izvoz i uvoz podešavanja i lista (prebacivanje sa telefona na TV)
- [ ] Prikaz više kanala odjednom (multi-view)
- [ ] Automatsko pokretanje pri paljenju uređaja (TV box)

### Faza 8: Izdanje

- [ ] Potvrđeni ime, package name, ikonica i TV baner
- [ ] Keystore napravljen, secrets dodati, `release.yml` radi
- [ ] R8/ProGuard pravila proverena na release buildu
- [ ] Verzionisanje (`versionCode` iz broja CI run-a ili iz taga)
- [ ] README: kako instalirati APK na telefon i na TV
- [ ] Prvo izdanje `v1.0.0` u GitHub Releases

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
