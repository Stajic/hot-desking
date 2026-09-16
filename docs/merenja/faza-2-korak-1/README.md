# Merenja — faza 2, korak 1 (autentikacija)

Merenje posle uvođenja JWT autentikacije i BCrypt lozinki (commit `2026226`),
u poređenju sa osnovnim merenjem [faze 1](../faza-1/README.md). Cilj je da se
vidi **šta je autentikacija koštala**: na javnim rutama, na zaštićenim rutama,
pri prijavi, pri podizanju i u veličini isporučenog artefakta.

Datum merenja: 17. septembar 2026.

---

## 1. Sažetak

| Pitanje | Odgovor |
|---|---|
| Da li su javne rute usporile? | **Ne.** Razlike su u okviru varijacije merenja. |
| Koliko košta provera tokena? | **≈ 2,5 µs** po zahtevu — oko 0,07 % trajanja zaštićenog zahteva. |
| Koliko košta prijava? | **≈ 426 ms** (medijana, 10 istovremenih korisnika) — namerno, zbog BCrypt-a. |
| Hladan start | **+514 ms** — hashovanje početnih lozinki. |
| Topao start | **nepromenjen** (−3 ms). |
| Veličina JAR-a | **+6,8 MB** (20,5 → 27,3 MB), objašnjeno u poglavlju 7. |
| Neuspelih zahteva | **0** od 281 562. |

Suština: autentikacija je skupa **jednom** — pri prijavi — a gotovo besplatna
**pri svakom sledećem zahtevu**. Prijava je oko 100 000 puta skuplja od provere
tokena, i to je upravo projektovani odnos.

---

## 2. Okruženje

Ista mašina i isti alati kao u fazi 1.

| | Faza 1 | Faza 2, korak 1 |
|---|---|---|
| Procesor | AMD Ryzen 7 5800H, 8/16 | isto |
| RAM ukupno / slobodno | 13,9 / 6,2 GB | 13,9 / **7,0 GB** |
| Pozadinsko opterećenje procesora | ~12 % | **~10 %** |
| Napajanje | mrežno | mrežno |
| JDK | Temurin 21.0.12.1 | isto |
| JVM zastavice | nijedna | nijedna |
| k6 | v2.2.0 | isto |
| Commit | `8ed000a` | `2026226` |

**Mašina je tokom ovog merenja bila nešto rasterećenija nego u fazi 1.** To je
važno za tumačenje poglavlja 5: sitna poboljšanja na javnim rutama ne treba
pripisati kodu.

---

## 3. Metodologija

Ista kao u fazi 1 (samostalni JAR, zagrevanje od 15 s se odbacuje, 30 s × 10
virtuelnih korisnika po ruti, preko `localhost`), uz sledeće dopune:

- **Hladni startovi u novim folderima.** Folderi iz faze 1 i dalje sadrže baze;
  da su ponovo korišćeni, „hladni" startovi bi zapravo bili topli.
- **Pre merenja zaštićene rute korisniku je napravljeno pet rezervacija**, da
  `GET /api/bookings/mine` ne bi merio prazan niz.
- **Prijava se meri poslednja**, jer BCrypt pod opterećenjem drži procesor
  zauzetim i zagreva mašinu, što bi uticalo na merenja posle nje.
- **Cena provere tokena meri se i izolovano**, bez HTTP-a i baze, jer razlika
  između zaštićene i javne rute uključuje i drugačiji upit nad bazom.

---

## 4. Vreme podizanja

Medijane; hladni start n = 3, topli n = 5.

| | Faza 1 | Faza 2, korak 1 | Razlika |
|---|---|---|---|
| Hladan — ukupno | 1 778 ms | 2 292 ms | **+514 ms** |
| Hladan — Ktor | 731 ms | 1 360 ms | +629 ms |
| Topao — ukupno | 1 764 ms | 1 761 ms | **−3 ms** |
| Topao — Ktor | 698 ms | 837 ms | +139 ms |

Sirovi podaci: [`startup.csv`](startup.csv)

**Zapažanje.** Hladan start je sporiji za pola sekunde, što odgovara ceni dva
BCrypt hasha pri upisu početnih podataka (cena 12, oko 250 ms po hashu). To je
unapred predviđeno i zabeleženo u komentaru u `auth/Security.kt`. Hladan start
se dešava jednom, nad praznom bazom.

Topao start je nepromenjen po ukupnom vremenu. Ktor-ov unutrašnji zapis je
porastao za 139 ms, što je u skladu sa inicijalizacijom biblioteka za
autentikaciju unutar modula; zašto se ukupno vreme ipak nije promenilo nije
utvrđeno bez detaljnijeg profilisanja podizanja, i ostaje otvoreno.

---

## 5. Odziv i propusnost

Sva vremena u milisekundama.

### Javne rute — poređenje sa fazom 1

| Ruta | | Zahteva/s | med | p90 | p95 | p99 | max |
|---|---|---|---|---|---|---|---|
| `/api/resources` | faza 1 | 3 250,4 | 2,90 | 3,63 | 3,92 | 4,58 | 11,11 |
| | **faza 2** | **3 326,0** | **2,85** | **3,52** | **3,78** | **4,37** | **8,10** |
| `/availability` | faza 1 | 3 161,4 | 2,97 | 3,74 | 4,05 | 5,04 | — |
| | **faza 2** | **3 311,9** | **2,82** | **3,53** | **3,83** | **4,87** | **32,30** |

**Zapažanje.** Propusnost javnih ruta je za 2,3 %, odnosno 4,8 % viša nego u
fazi 1. **To nije poboljšanje koje je doneo kod** — javne rute ne prolaze kroz
autentikaciju, a mašina je ovog puta bila rasterećenija (poglavlje 2). Ispravan
zaključak je da uvođenje `Authentication` plugina **nema merljiv trošak** na
rutama koje ga ne koriste.

Pojedinačni maksimum od 32,3 ms na `availability` je izolovan zahtev u repu
raspodele; p99 za istu rutu je 4,87 ms.

### Nove rute

| Ruta | Zahteva | Zahteva/s | Neuspelih | med | p90 | p95 | p99 | max |
|---|---|---|---|---|---|---|---|---|
| `GET /api/bookings/mine` (sa tokenom) | 81 706 | 2 723,3 | 0 | 3,40 | 4,34 | 4,79 | 6,49 | 26,58 |
| `POST /api/auth/login` | 701 | 23,1 | 0 | 426,51 | 454,90 | 467,42 | 480,45 | 490,66 |

Sirovi podaci: [`latency.csv`](latency.csv) i `summary-*.json`

---

## 6. Izolovana cena provere tokena

Provera i izdavanje tokena izvršavani su u petlji, bez HTTP-a i baze: posle
zagrevanja, pet rundi po 300 000 provera odnosno 100 000 izdavanja.

| Operacija | Medijana |
|---|---|
| Provera tokena (`JWTVerifier.verify`, HMAC-SHA256) | **2 473 ns ≈ 2,5 µs** |
| Izdavanje tokena (`JWT.create().sign`) | **2 117 ns ≈ 2,1 µs** |

Sirovi podaci: [`jwt-micro.txt`](jwt-micro.txt)

**Tumačenje.** Zaštićena ruta `mine` ima medijanu 3,40 ms, a javna `resources`
2,85 ms — razlika od 0,55 ms. Provera tokena od toga čini **oko 0,5 %**. Ostatak
otpada na drugačiji upit: `mine` spaja tri tabele (`bookings`, `resources`,
`users`) da bi vratio denormalizovana imena, dok `resources` čita jednu.

Odnos cene prijave i cene provere:

| | Trajanje |
|---|---|
| BCrypt provera lozinke (jedan zahtev, bez konkurencije) | ≈ 256 ms |
| Provera JWT tokena | ≈ 0,0025 ms |
| **Odnos** | **≈ 100 000 : 1** |

To je projektovani kompromis: skupa operacija se izvodi jednom, pri prijavi, i
njen rezultat — token — važi 24 sata. Svaki sledeći zahtev plaća samo proveru
potpisa.

**Ograda.** Ovo nije merenje alatom za mikrobenčmarke (JMH), nego petlja u
`jshell`-u uz zagrevanje. Broj treba shvatiti kao red veličine, ne kao preciznu
vrednost. Za zaključak da je provera tokena zanemariva — tri reda veličine ispod
trajanja zahteva — ta preciznost je dovoljna.

---

## 7. Prijava pod opterećenjem

Pojedinačna prijava traje oko 256 ms (izmereno pri razvoju koraka 1a). Pod
opterećenjem od 10 istovremenih korisnika medijana je **426 ms**, a propusnost
**23,1 prijava/s**.

Da je svih 10 hashovanja teklo potpuno paralelno, propusnost bi bila oko 39/s.
Izmerena vrednost odgovara **efektivno oko 6 istovremenih hashovanja**. Mogući
uzroci su: procesor ima 8 fizičkih jezgara, a BCrypt je čisto računski posao kod
kog logičke niti (SMT) ne udvostručuju kapacitet; k6 deli isti procesor sa
serverom; i moguće termalno usporavanje laptopa pri 30 s punog opterećenja.
**Koji od ovih uzroka dominira nije utvrđeno.**

**Da li je 23 prijave/s dovoljno?** Kao ilustracija, a ne izmereni zahtev: kada
bi se svih 1 000 zaposlenih u preduzeću prijavilo u istih pet minuta, to je oko
3,3 prijave/s — sedam puta manje od onoga što ovaj laptop obrađuje. Pošto token
važi 24 sata, stvarna učestalost prijava je daleko manja od toga.

---

## 8. Memorijski otisak

| Faza | RSS | Privatna | Gomila — zauzeto | Gomila — rezervisano |
|---|---|---|---|---|
| Posle starta (mirovanje) | 161,4 MB | 213,8 MB | 19,5 MB | 58 MB |
| Posle zagrevanja | 224,8 MB | 278,7 MB | 30,7 MB | 84 MB |
| Pod opterećenjem: `resources` | 238,4 MB | 287,0 MB | 34,6 MB | 84 MB |
| Pod opterećenjem: `availability` | 237,8 MB | 297,9 MB | 33,8 MB | 84 MB |
| Pod opterećenjem: `mine` | 243,3 MB | 293,1 MB | 42,0 MB | 84 MB |
| Pod opterećenjem: `login` | 274,5 MB | 323,6 MB | 48,5 MB | 84 MB |
| Posle prisilnog `GC.run` | 258,6 MB | 307,2 MB | **17,0 MB** | 68 MB |

Sirovi podaci: [`memory.csv`](memory.csv)

Poređenje sa fazom 1:

| | Faza 1 | Faza 2, korak 1 |
|---|---|---|
| RSS u mirovanju | 172,2 MB | 161,4 MB |
| Gomila stvarno zauzeta posle GC-a | 15,1 MB | **17,0 MB** |
| RSS posle GC-a | 190,4 MB | 258,6 MB |

**Zapažanje.** Ono što aplikacija zaista drži na gomili posle sakupljanja smeća
poraslo je za **1,9 MB** — to je stvarni memorijski trošak autentikacije.

RSS posle GC-a jeste veći za 68 MB, ali **te dve vrednosti nisu direktno
uporedive**: u ovom merenju pre `GC.run` su izvršene i rute `mine` i `login`,
kojih u fazi 1 nije bilo. Prijava pod opterećenjem proširuje radni skup procesa,
a JVM zauzetu memoriju ne vraća operativnom sistemu odmah.

---

## 9. Veličina isporučenog artefakta

Samostalni JAR je porastao sa **20,5 MB na 27,3 MB**. Svih 6,8 MB je razloženo:

| Biblioteka | Veličina | Zašto je prisutna |
|---|---|---|
| `guava` 33.6.0 | 2 993 KB | tranzitivno, preko `jwks-rsa` |
| `jackson-databind` 2.22 | 1 663 KB | tranzitivno, preko `java-jwt` |
| `ktor-client-core` 3.5.2 | 920 KB | tranzitivno, preko `ktor-server-auth` |
| `jackson-core` 2.22 | 580 KB | tranzitivno, preko `java-jwt` |
| `ktor-server-auth` 3.5.2 | 282 KB | direktna zavisnost |
| `ktor-server-sessions` 3.5.2 | 184 KB | tranzitivno, preko `ktor-server-auth` |
| `jackson-annotations` 2.22 | 82 KB | tranzitivno |
| `bytes` 1.5.0 | 79 KB | tranzitivno, preko `bcrypt` |
| `java-jwt` 4.6.0 | 64 KB | tranzitivno, preko `ktor-server-auth-jwt` |
| `ktor-server-auth-jwt` 3.5.2 | 44 KB | direktna zavisnost |
| `bcrypt` 0.10.2 | 40 KB | direktna zavisnost |
| `jwks-rsa` 0.24.1 | 22 KB | tranzitivno, preko `ktor-server-auth-jwt` |
| **Ukupno** | **≈ 6,81 MB** | |

**Zapažanje relevantno za pregled ekosistema.** Od tri direktne zavisnosti
(ukupno 366 KB), ostatak od 6,4 MB su tranzitivne biblioteke, i nijedna od tri
najveće se u ovom projektu ne koristi:

- **Guava** dolazi uz `jwks-rsa`, koji služi za preuzimanje javnih RSA ključeva
  sa JWKS adrese. Projekat koristi HMAC potpis sa deljenom tajnom i JWKS ne koristi.
- **Jackson** dolazi uz `java-jwt`, Javinu biblioteku za JWT. Projekat za JSON
  koristi `kotlinx.serialization`, pa isporučeni artefakt sada nosi **dve
  nezavisne biblioteke za serijalizaciju**.
- **Ktor HTTP klijent** dolazi uz `ktor-server-auth`, koji ga koristi za OAuth
  tokove. Projekat OAuth ne koristi.

Ovo je konkretna ilustracija odnosa Kotlin ekosistema prema Javinom: Ktor-ova
JWT podrška je tanak omotač oko Java biblioteke, koja sa sobom donosi Java
ekosistem. Funkcionalno je sve ispravno, ali po veličini i broju zavisnosti
rešenje nije „čist Kotlin".

Isključivanje `jwks-rsa` iz zavisnosti verovatno bi uklonilo Guavu, ali to nije
proveravano i nosi rizik greške u vreme izvršavanja ako Ktor tu biblioteku ipak
učitava.

---

## 10. Ograničenja

Sva ograničenja iz [faze 1](../faza-1/README.md#7-ograničenja) i dalje važe. Uz njih:

- **Razlika između `mine` i `resources` nije čista cena autentikacije** — rute
  izvršavaju različite upite. Zato postoji izolovano merenje u poglavlju 6.
- **Izolovano merenje tokena nije JMH.** Pouzdano je kao red veličine.
- **Uzrok ograničene paralelnosti prijave nije utvrđen** (poglavlje 7).
- **RSS posle GC-a nije uporediv sa fazom 1** zbog drugačijeg redosleda (poglavlje 8).
- **Ilustracija sa 1 000 zaposlenih je pretpostavka**, ne izmereni ni zadati zahtev.

---

## 11. Kako ponoviti

```bash
.\gradlew.bat :server:buildFatJar
```

Skripte se nalaze u [`skripte/`](skripte/). Kao i u fazi 1, sadrže apsolutne
putanje razvojne mašine.
