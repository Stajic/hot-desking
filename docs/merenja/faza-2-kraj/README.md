# Merenja — kraj faze 2

Treća tačka u nizu merenja, nad stanjem posle koraka 4 (commit `60daee4`): prijava,
mreža slotova i rezervacija u aplikaciji, moje rezervacije i prava vremenska zona
preko `kotlinx-datetime`. Poređenje sa [fazom 1](../faza-1/README.md) i sa merenjem
[posle autentikacije](../faza-2-korak-1/README.md).

Datum merenja: 5. oktobar 2026.

---

## 1. Sažetak

**Između merenja posle autentikacije i kraja faze 2 nema merljive promene na
serveru.** Koraci 2, 3a i 3b bili su isključivo klijentski, a jedina serverska
izmena — `BookingRules` preko `kotlinx-datetime` — košta oko 0,1 µs po pozivu, što
je četiri reda veličine ispod trajanja jednog zahteva.

| | Faza 1 | Posle autentikacije | **Kraj faze 2** |
|---|---|---|---|
| Hladan start (do prvog odgovora) | 1 778 ms | 2 292 ms | **2 316 ms** |
| Topao start | 1 764 ms | 1 761 ms | **1 770 ms** |
| RSS u mirovanju | 172,2 MB | 161,4 MB | **154,3 MB** |
| Gomila stvarno zauzeta posle GC-a | 15,1 MB | 17,0 MB | **17,0 MB** |
| `GET /api/resources` — zahteva/s | 3 250 | 3 326 | **3 305** |
| `GET /api/resources` — medijana / p99 | 2,90 / 4,58 ms | 2,85 / 4,37 ms | **2,84 / 4,49 ms** |
| `GET /availability` — zahteva/s | 3 161 | 3 312 | **3 317** |
| `GET /availability` — medijana / p99 | 2,97 / 5,04 ms | 2,82 / 4,87 ms | **2,79 / 4,86 ms** |
| `GET /api/bookings/mine` — medijana / p99 | — | 3,40 / 6,49 ms | **3,29 / 6,18 ms** |
| `POST /api/auth/login` — zahteva/s / medijana | — | 23,1 / 427 ms | **22,6 / 440 ms** |
| Samostalni JAR | 20,5 MB | 27,3 MB | **27,3 MB** (+20 B) |
| Neuspelih zahteva | 0 / 192 365 | 0 / 281 562 | **0 / 283 655** |

Ceo prelaz faze 1 u fazu 2 sveo se na tri merljive cene, sve tri objašnjene u
[merenju posle autentikacije](../faza-2-korak-1/README.md): hladan start +0,5 s
(BCrypt pri upisu početnih podataka), +1,9 MB stvarno zauzete memorije, i +6,8 MB
JAR-a od tranzitivnih zavisnosti autentikacije.

---

## 2. Okruženje

Ista mašina, alati i metodologija kao u prethodna dva merenja.

| | Faza 1 | Posle autentikacije | Kraj faze 2 |
|---|---|---|---|
| Slobodan RAM pred merenje | 6,2 GB | 7,0 GB | 5,5 GB |
| Pozadinsko opterećenje procesora | ~12 % | ~10 % | ~10 % |
| Napajanje | mrežno | mrežno | mrežno |
| Commit | `8ed000a` | `2026226` | `60daee4` |

AMD Ryzen 7 5800H (8/16), 13,9 GB RAM, Windows 11, Temurin JDK 21.0.12.1 bez
dodatnih JVM zastavica, k6 v2.2.0. Svaki hladni start u novom, praznom folderu.

---

## 3. Vreme podizanja

Medijane; hladan n = 3, topao n = 5.

| | Faza 1 | Posle autentikacije | Kraj faze 2 |
|---|---|---|---|
| Hladan — ukupno | 1 778 ms | 2 292 ms | 2 316 ms |
| Hladan — Ktor | 731 ms | 1 360 ms | 1 346 ms |
| Topao — ukupno | 1 764 ms | 1 761 ms | 1 770 ms |
| Topao — Ktor | 698 ms | 837 ms | 830 ms |

Sirovi podaci: [`startup.csv`](startup.csv)

Razlike u odnosu na merenje posle autentikacije su 24 ms i 9 ms — u okviru
varijacije između pokretanja.

---

## 4. Odziv i propusnost

10 virtuelnih korisnika, 30 s po ruti, posle zagrevanja od 15 s. Vremena u ms.

| Ruta | Zahteva | Zahteva/s | Neuspelih | med | p90 | p95 | p99 | max |
|---|---|---|---|---|---|---|---|---|
| `/api/resources` | 99 151 | 3 304,8 | 0 | 2,84 | 3,59 | 3,87 | 4,49 | 9,86 |
| `/api/resources/{id}/availability` | 99 524 | 3 317,2 | 0 | 2,79 | 3,57 | 3,89 | 4,86 | 820,63 ¹ |
| `/api/bookings/mine` (sa tokenom) | 84 292 | 2 809,6 | 0 | 3,29 | 4,28 | 4,73 | 6,18 | 24,91 |
| `/api/auth/login` | 688 | 22,6 | 0 | 439,88 | 468,03 | 476,88 | 517,28 | 538,90 |

Sirovi podaci: [`latency.csv`](latency.csv) i `summary-*.json`

¹ **Izolovan zahtev, nije ponovljen.** Jedan od 99 524 zahteva trajao je 820 ms;
p99 iste rute je 4,86 ms. Pošto je ova ruta jedina na kojoj je promenjen kod
(`dayStart` sada ide kroz `kotlinx-datetime`), skok je posebno proveren na svežem
serveru, istom pripremom i sa uključenim zapisom sakupljača smeća:

| Provera | Rezultat |
|---|---|
| Prvi zahtev na `/availability` posle starta (hladna putanja) | 28,6 ms |
| Ponovljen prolaz 1 — max | 18,75 ms |
| Ponovljen prolaz 2 — max | 73,56 ms |
| Najduža GC pauza (od 303 zabeležene) | 9,33 ms |

Skok se nije ponovio, hladna putanja nove biblioteke ga ne objašnjava, a GC pauze
su dva reda veličine kraće. Najverovatniji uzrok je zastoj okruženja na razvojnom
laptopu (raspoređivanje procesa, Windows Defender); tačan uzrok nije utvrđen.
Podaci provere: [`repro/`](repro/).

---

## 5. Izolovana cena `kotlinx-datetime`

Na HTTP rutama razlika se gubi u šumu, pa je `BookingRules` meren direktno:
stara verzija (commit `09a5871`, fiksni pomeraj +2 h) naspram nove (`60daee4`,
`TimeZone.of("Europe/Belgrade")`). Po 5 rundi od 2 000 000 poziva posle zagrevanja,
u odvojenim JVM-ovima.

| | Stara | Nova | Odnos |
|---|---|---|---|
| `validate()` | 0,40 ns | 91,6 ns | ~230× |
| `dayStart()` | 0,97 ns | 107 ns | ~110× |

Sirovi podaci: [`rules-micro.csv`](rules-micro.csv)

**Tumačenje.** Relativno je nova verzija dva reda veličine sporija: stara je čista
celobrojna aritmetika, nova pretvara trenutak u lokalno vreme preko pravila
vremenske zone. Apsolutno, to je oko **0,1 µs po pozivu**, dok jedan zahtev traje
oko 2 800 µs — udeo od približno **0,004 %**. Ruta `availability` poziva `dayStart`
jednom po zahtevu, i njena medijana je ostala 2,79 ms naspram 2,82 ms.

Ovo je primer zašto se relativni rezultati mikrobenčmarka ne prenose direktno na
sistem: „100 puta sporije" i „nemerljivo na sistemu" su ovde oba tačna.

**Ograde.**
- Merenje je petlja u `jshell`-u, ne JMH. Pouzdano je kao red veličine.
- Prva verzija ovog merenja dala je 0 ns za staru implementaciju, jer je JIT
  izračunao poziv sa konstantnim argumentima jednom i izbacio ga iz petlje. Ulaz se
  zato menja u svakoj iteraciji. Za `validate` su početak i kraj termina i dalje
  konstantni, pa je 0,40 ns verovatno potcenjena vrednost; `dayStart` od 0,97 ns,
  gde se ulaz stvarno menja, verodostojniji je red veličine stare implementacije.

---

## 6. Memorijski otisak

| Faza | RSS | Privatna | Gomila — zauzeto | Gomila — rezervisano |
|---|---|---|---|---|
| Posle starta (mirovanje) | 154,3 MB | 205,4 MB | 20,4 MB | 50 MB |
| Posle zagrevanja | 263,7 MB | 325,2 MB | 47,2 MB | 136 MB |
| Pod opterećenjem: `resources` | 268,3 MB | 330,3 MB | 50,9 MB | 136 MB |
| Pod opterećenjem: `availability` | 274,2 MB | 340,7 MB | 68,2 MB | 136 MB |
| Pod opterećenjem: `mine` | 282,1 MB | 349,5 MB | 92,0 MB | 136 MB |
| Pod opterećenjem: `login` | 297,3 MB | 373,8 MB | 67,8 MB | 136 MB |
| Posle prisilnog `GC.run` | 231,1 MB | 304,4 MB | **17,0 MB** | 68 MB |

Sirovi podaci: [`memory.csv`](memory.csv)

Posle sakupljanja smeća aplikacija drži **17,0 MB** — isto kao posle
autentikacije. `kotlinx-datetime` nije povećao stvarno zauzetu memoriju.

---

## 7. Veličina JAR-a i jedna zavisnost koja je već bila tu

Samostalni JAR je porastao za **20 bajtova** (28 659 250 → 28 659 270), iako je
dodata cela biblioteka. Razlog: `kotlinx-datetime` je u JAR-u bio i pre koraka 4 —
448 klasa, koje je tranzitivno doneo **Exposed** (`exposed-core`), u varijanti
`0.7.1-0.6.x-compat`. Eksplicitna zavisnost `0.8.0` samo je zamenila tu verziju.

To je otvorilo pitanje sukoba: varijanta `-0.6.x-compat` sadrži staru klasu
`kotlinx.datetime.Instant`, koje u `0.8.0` više nema (prešla je u standardnu
biblioteku kao `kotlin.time.Instant`). Da je Exposed koristi, server bi pukao u
vreme izvršavanja. Provereno je pregledom svih klasa u `exposed-core-1.0.0.jar`:
**nijedna ne referencira `kotlinx/datetime/Instant`**, pa sukoba nema.

Zapažanje za pregled ekosistema: biblioteke Kotlin ekosistema dele zajedničke
`kotlinx` biblioteke, pa dodavanje „nove" zavisnosti često znači preuzimanje
kontrole nad verzijom koja je već prisutna. Prelaz `Instant` klase iz
`kotlinx-datetime` u standardnu biblioteku je primer prelomne promene koju
ekosistem ublažava paralelnim `-compat` artefaktima.

---

## 8. Ograničenja

Sva ograničenja iz [faze 1](../faza-1/README.md#7-ograničenja) i
[merenja posle autentikacije](../faza-2-korak-1/README.md#10-ograničenja) i dalje
važe. Uz njih:

- **Uzrok izolovanog zahteva od 820 ms nije utvrđen** — samo je isključeno da ga
  izaziva nova biblioteka ili sakupljač smeća.
- **Izolovano merenje `BookingRules` nije JMH**, a stara vrednost za `validate` je
  verovatno potcenjena (poglavlje 5).
- **Veličina APK-a nije merena.** Veličina debug APK-a zavisi od stanja
  inkrementalnog build-a (pun build i build iz keša daju različite bajtove za isti
  kod); za pouzdano poređenje potrebni su čisti build-ovi obe verzije.

---

## 9. Kako ponoviti

Skripte su u [`skripte/`](skripte/), sa apsolutnim putanjama razvojne mašine kao i
u prethodnim merenjima. Za izolovano merenje `BookingRules` potreban je i JAR stare
verzije, dobijen iz commit-a `09a5871` preko `git archive`.
