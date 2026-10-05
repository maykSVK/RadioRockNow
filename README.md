# Rádio ROCK Now

Aplikácia pre **Android Auto**, ktorá na karte médií (pravý panel) zobrazuje, **čo práve hrá na [Rádiu ROCK](https://radiorock.sk)**, vrátane krátkych zaujímavostí o skladbe a interpretovi v slovenčine.

Aplikácia **nič neprehráva** – rádio počúvaš ďalej z FM antény v aute. Slúži len ako informačný „widget" (karta médií).

## Čo zobrazuje
- názov skladby a interpreta,
- obal albumu (Cover Art Archive), inak logo Rádia ROCK,
- striedavo krátke zaujímavosti: rok vydania, album, pôvod interpreta, rok vzniku/narodenia, žáner a prvú vetu z Wikipédie (slovenskej, inak anglickej),
- dlhé texty sa na karte posúvajú ako bežiaci text,
- v appke na telefóne je zobrazený aj dlhší text.

## Ako to funguje
1. Pripojí sa na stream `https://stream.bauermedia.sk/rock-lo.mp3` s hlavičkou `Icy-MetaData: 1`, zvuk zahadzuje a číta len ICY metadáta (`StreamTitle`).
2. Podľa názvu skladby a interpreta vyhľadá údaje v [MusicBrainz](https://musicbrainz.org), obal v [Cover Art Archive](https://coverartarchive.org) a úvod interpreta cez Wikidata / Wikipédiu.
3. Cez `MediaBrowserService` + `MediaSession` ich pošle do Android Auto.

> Poznámka: údaje pochádzajú z online streamu, ktorý môže byť oproti FM vysielaniu oneskorený o niekoľko sekúnd.

## Inštalácia
1. Stiahni APK z [Releases](../../releases) (alebo zo zložky [`releases/`](releases)) a nainštaluj ho na telefón.
2. V aplikácii **Android Auto** zapni vývojársky režim (10× klepni na „Verzia") a v nastaveniach pre vývojárov povoľ **Neznáme zdroje**.
3. Odpoj a znova pripoj telefón k autu. Aplikáciu „Rádio ROCK Now" nájdeš v ponuke aplikácií (médiá).

## Build zo zdrojov
Potrebuješ JDK 17+ a Android SDK (platforma 34, build-tools 34.0.0). V `local.properties` nastav `sdk.dir`.

```
./gradlew assembleDebug
```

APK sa vytvorí v `app/build/outputs/apk/debug/`.

## Technológie
Java, Android framework `MediaBrowserService`/`MediaSession`, bez externých knižníc. minSdk 26.

## Poznámky
Projekt je neoficiálny a nie je spojený s Bauer Media Slovakia. Dáta pochádzajú z verejných zdrojov (stream rádia, MusicBrainz, Wikipédia).
