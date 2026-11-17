# Glowbox (IPTV plejer)

Besplatan IPTV plejer za Android telefon i Android TV. Aplikacija ne sadrži nikakav
sadržaj: korisnik dodaje sopstvene liste (M3U ili Xtream Codes nalog).

**Stanje:** liste (M3U link, M3U fajl, Xtream Codes), kanali uživo na telefonu i TV-u, favoriti,
pretraga, TV vodič (EPG), arhiva, filmovi i serije sa nastavkom gledanja, Trakt, PIN na kategorijama,
multi-view, slanje na TV (Chromecast ili ova aplikacija na TV-u) i snimanje kanala. Sledi prvo
zvanično izdanje (Faza 8 u planu).

- [PLAN.md](PLAN.md): detaljan plan, faze i odluke
- [CLAUDE.md](CLAUDE.md): pravila za rad kroz Claude Code

## Preuzimanje APK-a

Postoje dve vrste APK-a: zvanična izdanja i test (dev) buildovi. Potpisane su istim ključem i dele
brojanje verzija, pa se sa jedne na drugu prelazi običnim ažuriranjem, bez brisanja aplikacije: u
Podešavanjima se izabere „Verzija za ažuriranje” (zvanična izdanja ili test buildovi). Android ne
dozvoljava vraćanje na stariji build, pa aplikacija prelazi tek kad na izabranoj verziji izađe build
noviji od instaliranog.

**Zvanično izdanje** (`iptv-player-<verzija>.apk`): stranica **Releases** u repou. Pravi se samo kad se
u `main` spoji nova verzija u `version.txt` (vidi „Novo izdanje”). Aplikacija se posle sama ažurira na sledeća izdanja
(Podešavanja, „Proveri ažuriranje”).

**Dev build** (`iptv-player-debug.apk`): pravi se posle svakog spajanja u `main` i služi za probu:

<https://github.com/mcbane23/iptv-player/releases/download/dev-latest/iptv-player-debug.apk>

Link je uvek isti i vodi na poslednji spojen kod. Grane ne objavljuju APK, samo proveravaju build
i testove.
Dev build sa `main`-a potpisan je istim ključem kao zvanična izdanja (CI ga čita iz GitHub Secrets),
pa se instalira preko izdanja i preko ranijih dev buildova. Buildovi stariji od ove promene bili su
potpisani debug ključem: preko njih novi ne može, pa se aplikacija jednom obriše.

## Instalacija

**Telefon:** prebaci APK na telefon, otvori ga i dozvoli instalaciju iz nepoznatih
izvora kad Android to zatraži.

**Android TV / TV box:** najlakše preko ADB-a sa računara u istoj mreži
(na TV-u uključi *Developer options* i *Network/USB debugging*):

```
adb connect <ip-adresa-tv-a>
adb install -r iptv-player-1.0.0.apk
```

Druga mogućnost je aplikacija za prenos fajlova ili USB fleš, pa instalacija preko
fajl menadžera na TV-u. Aplikacija se pojavljuje i u običnom launcher-u telefona
i u Android TV launcher-u.

Bez računara: na TV-u instaliraj aplikaciju „Downloader” iz prodavnice, upiši u nju link ka APK-u
i dozvoli joj instalaciju iz nepoznatih izvora. Poslednje zvanično izdanje je uvek na istoj adresi:

```
https://github.com/mcbane23/apps/releases/download/iptv-player/glowbox.apk
```

Da se adresa ne kuca daljinskim, u Downloader može da se upiše samo kratki kod `6087907`
(isto što i `aftv.news/6087907`), koji vodi na tu adresu.

## Novo izdanje

Jednom, pre prvog izdanja, pravi se ključ za potpis. Sačuvaj ga na sigurnom mestu (i kopiju van
računara), zajedno sa lozinkom. Ako se ključ izgubi, nova izdanja više ne mogu da se instaliraju
preko starih.

**Bez alata na računaru (samo pregledač):**

1. U repou, **Settings → Secrets and variables → Actions**, dodaj secret `RELEASE_KEYSTORE_PASSWORD`
   sa lozinkom po izboru (najmanje 8 znakova). Zapiši je.
2. **Actions → Napravi ključ za potpis → Run workflow.**
3. Kad se završi, na dnu stranice preuzmi artifact `kljuc-za-potpis` (briše se posle jednog
   dana). U ZIP-u su `release.jks` (ključ, njega čuvaš) i `RELEASE_KEYSTORE_BASE64.txt`.
4. Ceo sadržaj tekstualnog fajla nalepi u novi secret `RELEASE_KEYSTORE_BASE64`.

**Sa `keytool`-om (dolazi uz JDK):**

```
keytool -genkeypair -v -keystore release.jks -storetype PKCS12 -alias release \
  -keyalg RSA -keysize 4096 -validity 10000
base64 -w0 release.jks
```

Zatim dodaj secrets `RELEASE_KEYSTORE_BASE64` (ispis komande `base64`) i `RELEASE_KEYSTORE_PASSWORD`.
Ako ključ nema ime `release` ili ima posebnu lozinku, dodaj i `RELEASE_KEY_ALIAS` i `RELEASE_KEY_PASSWORD`.

Ključ i lozinke nikad ne idu u repo.

Izdanje se pravi samo od sebe: u fajlu `version.txt` promeni se verzija (npr. `1.0.0` u `1.0.1`) i ta
izmena se spoji u `main` kroz pull request. Workflow **Release** tada pokreće testove, pravi potpisan
APK sa uključenim R8, postavlja oznaku `v1.0.1`, objavljuje APK u **Releases** i u javni repo iz kog se
aplikacija ažurira. Ako oznaka za tu verziju već postoji, ne radi ništa. Ime verzije je ono iz
`version.txt`, a broj verzije (`versionCode`) je 1 000 000 + broj commitova u `main`-u, isti račun kao
za dev buildove.

## Build

Lokalni build: `./gradlew assembleDebug` (potreban JDK 17 ili noviji i Android SDK).
