# IPTV Player

Besplatan IPTV plejer za Android telefon i Android TV. Aplikacija ne sadrži nikakav
sadržaj: korisnik dodaje sopstvene liste (M3U ili Xtream Codes nalog).

**Stanje:** Faza 0 (kostur projekta i CI). Aplikacija za sada prikazuje samo početni ekran.

- [PLAN.md](PLAN.md): detaljan plan, faze i odluke
- [CLAUDE.md](CLAUDE.md): pravila za rad kroz Claude Code

## Preuzimanje APK-a

APK se bilduje automatski preko GitHub Actions na svaki push i pull request.

1. Otvori karticu **Actions** u repou i izaberi workflow **Build**.
2. Klikni na poslednji uspešan run (zelena kvačica) za granu koja te zanima (obično `main`).
3. Na dnu stranice, u odeljku **Artifacts**, preuzmi `iptv-player-debug-<broj>`.
4. GitHub daje ZIP; raspakuj ga i dobićeš `app-debug.apk`.

**Direktan link (bez ZIP-a):** svaki push objavljuje APK i kao pre-release `dev-latest`:

<https://github.com/mcbane23/iptv-player/releases/download/dev-latest/iptv-player-debug.apk>

Link je uvek isti i vodi na poslednji build. Pošto je repo privatan, radi samo u
pregledaču u kome si prijavljen na GitHub.

Debug APK je uvek potpisan istim ključem (`keystore/debug.keystore`), pa novi APK
može da se instalira preko starog bez brisanja aplikacije.

## Instalacija

**Telefon:** prebaci APK na telefon, otvori ga i dozvoli instalaciju iz nepoznatih
izvora kad Android to zatraži.

**Android TV / TV box:** najlakše preko ADB-a sa računara u istoj mreži
(na TV-u uključi *Developer options* i *Network/USB debugging*):

```
adb connect <ip-adresa-tv-a>
adb install -r app-debug.apk
```

Druga mogućnost je aplikacija za prenos fajlova ili USB fleš, pa instalacija preko
fajl menadžera na TV-u. Aplikacija se pojavljuje i u običnom launcher-u telefona
i u Android TV launcher-u.

## Build

Lokalni build: `./gradlew assembleDebug` (potreban JDK 17 ili noviji i Android SDK).
