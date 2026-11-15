# IPTV plejer (Android telefon + Android TV)

Sopstvena, potpuno besplatna IPTV aplikacija. Detaljan plan, faze i odluke su u
[`PLAN.md`](PLAN.md). **Pročitaj PLAN.md pre bilo kakvog rada** i nastavi od prve
nezavršene faze.

## Pravila projekta

- Aplikacija ne sadrži nikakav sadržaj. Nikada ne dodavati ugrađene liste, kanale,
  linkove ka strimovima ni podatke za prijavu, ni u kod, ni u testove, ni u dokumentaciju.
- Bez reklama, plaćanja, probnog perioda, analitike i praćenja.
- Kod je originalan. Ne dekompajlirati i ne kopirati kod, ikonice, nazive ni izgled
  drugih IPTV aplikacija (Hot Player Plus, TiViMate, Smarters). One su samo
  inspiracija za spisak funkcija.
- Tajne (keystore, lozinke, tokeni) nikada ne idu u repo. Samo GitHub Secrets.
  Jedini izuzetak je `keystore/debug.keystore`: stalan debug ključ, namerno u repou.
  Ne brisati ga i ne generisati ponovo, i ne koristiti ga za release.

## Tehnologije

Kotlin, Jetpack Compose + Compose for TV, Media3 (ExoPlayer), Room, Retrofit/OkHttp,
kotlinx.serialization, Coil, DataStore, Hilt, Coroutines/Flow, WorkManager.
Gradle Kotlin DSL sa version catalog-om. Jedan APK za telefon i TV.

## Build

**Lokalni build u cloud okruženju najčešće ne radi** (Google i Maven serveri su
blokirani). Ne trošiti vreme na zaobilaženje. Build i testovi idu preko GitHub Actions:

```
git push
gh run list --limit 3
gh run view <id> --log-failed
```

Faza ili izmena nije gotova dok CI nije zelen. Zbog toga: male izmene, čest push.

## Način rada

- Sa vlasnikom (Bane) komunicirati na srpskom (latinica).
- Kad nešto nije jasno ili postoji više razumnih rešenja, prvo pitati, pa tek onda raditi.
  Otvorene odluke su popisane u PLAN.md, odeljak 3.
- Jedna faza = jedna grana (`faza-N-kratak-opis`) + pull request. Ne pushovati
  direktno na `main` posle početnog commita.
- Kad se stavka završi, označiti je u PLAN.md u istom pull request-u.
- Svi tekstovi u interfejsu idu u `strings.xml` (srpski i engleski), ne u kod.
- Svaki ekran mora da radi i na dodir i samo sa daljinskim upravljačem (D-pad).
- Parseri (M3U, XMLTV) moraju imati unit testove sa izmišljenim podacima.
