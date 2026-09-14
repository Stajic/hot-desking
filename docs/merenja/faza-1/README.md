# Merenja — faza 1

Osnovno („pre") merenje serverske komponente, izvedeno nad stanjem označenim
tagom [`faza-1`](https://github.com/Stajic/hot-desking/releases/tag/faza-1)
(commit `8ed000a`). Svrha je referentna tačka: svako kasnije merenje pokazuje
koliko je koja dodata funkcionalnost koštala.

Datum merenja: 14. septembar 2026.

---

## 1. Sažetak

| Metrika | Vrednost |
|---|---|
| Vreme podizanja (hladan start, do prvog odgovora) | **1,78 s** |
| Memorijski otisak u mirovanju (RSS) | **172 MB** |
| Memorijski otisak posle opterećenja i GC-a (RSS) | **190 MB** |
| `GET /api/resources` — propusnost | **3 250 zahteva/s** |
| `GET /api/resources` — odziv, medijana / p99 | **2,90 ms / 4,58 ms** |
| `GET /api/resources/{id}/availability` — propusnost | **3 161 zahteva/s** |
| `GET /api/resources/{id}/availability` — odziv, medijana / p99 | **2,97 ms / 5,04 ms** |
| Neuspelih zahteva (ukupno 192 365) | **0** |

---

## 2. Okruženje

Bez ovih podataka brojevi nisu ponovljivi.

| | |
|---|---|
| Procesor | AMD Ryzen 7 5800H, 8 jezgara / 16 niti, do 3 201 MHz |
| RAM | 13,9 GB (6,2 GB slobodno pred merenje) |
| OS | Windows 11 Home 10.0.26200 |
| Napajanje | mrežno (ne baterija — izbegnuto throttlovanje procesora) |
| JDK | Temurin 21.0.12.1 LTS |
| JVM — najveća gomila | 3 548 MB (podrazumevano, ¼ RAM-a) |
| JVM — početna gomila | 222 MB |
| JVM — sakupljač | G1GC (podrazumevani) |
| JVM zastavice | nijedna dodata |
| Alat za opterećenje | k6 v2.2.0 |
| Pozadinsko opterećenje procesora | ~12 % |

**Napomena o uslovima:** merenje je izvedeno na razvojnom laptopu, ne na
namenskom serveru. Tokom merenja aktivni su bili Windows Defender i razvojni
alati (~12 % procesora). Brojevi su zato pre konzervativni nego optimistični.

---

## 3. Metodologija

**Meri se samostalni JAR, ne `gradlew run`.** Pokretanje kroz Gradle uvelo bi u
merenje Gradle demon i njegov overhead, čime bi vreme podizanja izgubilo smisao.
Koristi se `:server:buildFatJar` (20,5 MB) pokrenut sa `java -jar`.

**Mere se samo `GET` rute.** `POST /api/bookings` menja stanje baze, pa dva
uzastopna merenja ne bi bila uporediva. Rute pod merenjem:

- `/api/resources` — upit nad bazom i serijalizacija
- `/api/resources/{id}/availability` — isto, uz izračunavanje mreže od 24 slota
  kroz `BookingRules` iz deljenog modula

**Zagrevanje se odbacuje.** Pre merenja izvršava se prolaz od 15 s sa 5
virtuelnih korisnika, čiji se rezultat ne beleži. Bez toga bi u brojeve ušlo
vreme dok JIT prevodilac ne optimizuje vruće putanje.

**Vreme podizanja** meri se od pokretanja JVM procesa do prvog odgovora `200` na
`/health` — dakle uključuje start JVM-a, učitavanje klasa, povezivanje na bazu i
podizanje Netty-ja. Odvojeno se beleži i Ktor-ov sopstveni zapis
(`Application started in …`), jer razlika između ta dva broja pokazuje koliko
troši sam JVM pre nego što aplikacija uopšte počne.

**Opterećenje:** 10 virtuelnih korisnika, 30 s po ruti, preko `localhost`.

---

## 4. Vreme podizanja

Tri hladna starta (prazan folder — kreiranje šeme i upis početnih podataka) i pet
toplih (baza već postoji). Prikazane su medijane.

| Tip starta | Ukupno (do prvog odgovora) | Od toga Ktor | Razlika = JVM i učitavanje klasa |
|---|---|---|---|
| Hladan | 1 778 ms | 731 ms | 1 047 ms |
| Topao | 1 764 ms | 698 ms | 1 066 ms |

Sirovi podaci: [`startup.csv`](startup.csv)

**Zapažanje.** Razlika između hladnog i toplog starta je 14 ms. Kreiranje pet
tabela i upis osam resursa sa opremom praktično ne košta ništa. Dominantan
trošak podizanja nije aplikacija nego JVM: oko 1,05 s od ukupno 1,78 s otpada na
start virtuelne mašine i učitavanje klasa, pre nego što Ktor uopšte počne da meri.

---

## 5. Memorijski otisak

| Faza | RSS | Privatna memorija | Gomila — zauzeto | Gomila — rezervisano |
|---|---|---|---|---|
| Posle starta (mirovanje) | 172,2 MB | 223,8 MB | 30,4 MB | 50 MB |
| Posle zagrevanja | 254,8 MB | 324,1 MB | 46,8 MB | 136 MB |
| Pod opterećenjem (`resources`) | 260,4 MB | 323,3 MB | 77,5 MB | 136 MB |
| Pod opterećenjem (`availability`) | 266,2 MB | 344,1 MB | 18,0 MB | 136 MB |
| Posle prisilnog `GC.run` | 190,4 MB | 266,8 MB | 15,1 MB | 60 MB |

Sirovi podaci: [`memory.csv`](memory.csv)

**Zapažanje.** Posle prisilnog sakupljanja smeća aplikacija zaista drži **15 MB**
na gomili — sve preko toga je rezerva koju JVM nije vratio operativnom sistemu, i
prostor koji G1 drži jer nema pritiska da ga oslobodi. Radni skup od 172 MB u
mirovanju je dakle pretežno trošak same virtuelne mašine, ne podataka aplikacije.
To je očekivano za JVM i treba ga tako i tumačiti pri poređenju sa platformama
koje nemaju upravljanu memoriju.

---

## 6. Odziv i propusnost

10 virtuelnih korisnika, 30 s po ruti, posle odbačenog zagrevanja. Sva vremena u
milisekundama.

| Ruta | Zahteva | Zahteva/s | Neuspelih | min | med | avg | p90 | p95 | p99 | max |
|---|---|---|---|---|---|---|---|---|---|---|
| `/api/resources` | 97 518 | 3 250,4 | 0 | 0,52 | 2,90 | 2,98 | 3,63 | 3,92 | 4,58 | 11,11 |
| `/api/resources/{id}/availability` | 94 847 | 3 161,4 | 0 | 0,50 | 2,97 | 3,06 | 3,74 | 4,05 | 5,04 | — |

Sirovi podaci: [`latency.csv`](latency.csv),
[`summary-resources.json`](summary-resources.json),
[`summary-availability.json`](summary-availability.json)

**Zapažanje koje je relevantno za hipotezu rada.** Ruta `availability` pored upita
nad bazom izvršava i `BookingRules.dayStart`, `slotsPerDay` i konstrukciju mreže
od 24 slota — dakle svu logiku validacije iz deljenog modula — a sporija je za
svega **2,7 %** po medijani. Deljena poslovna logika koja se izvršava i na
klijentu i na serveru ne predstavlja merljiv trošak na serverskoj strani.

Nijedan od 192 365 zahteva nije pao.

---

## 7. Ograničenja

Navedena svesno; svako od njih smanjuje domet zaključaka.

- **Nema komparatora.** Apsolutni brojevi bez tačke poređenja ne dokazuju da je
  performansa „adekvatna za serverski deo". Sledeći korak je merenje ekvivalentnog
  servisa na drugoj platformi (npr. Spring Boot) na **istoj mašini**, ili poređenje
  sa javno objavljenim rezultatima (TechEmpower Framework Benchmarks).
- **Merenje preko `localhost`.** Mrežno kašnjenje je isključeno. To izoluje trošak
  servera, ali precenjuje ono što bi stvarni klijent video.
- **Nije tražena tačka zasićenja.** Pri 10 virtuelnih korisnika sistem nije
  doveden do granice; izmerena propusnost nije maksimalna, nego propusnost pri
  zadatom opterećenju.
- **Razvojni laptop, ne server.** Uslovi su dokumentovani u poglavlju 2.
- **Samo rute za čitanje.** Pisanje (`POST /api/bookings`) ulazi u merenje u
  fazi 2, kada rutina bude imala i smisla za ponovljeno merenje.

---

## 8. Kako ponoviti

```bash
.\gradlew.bat :server:buildFatJar
```

```bash
java -jar server\build\libs\server-all.jar
```

Skripte za merenje i k6 scenario nalaze se u [`skripte/`](skripte/).
