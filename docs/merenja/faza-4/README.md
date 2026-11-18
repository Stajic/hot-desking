# Merenja: faza 4 (PostgreSQL)

Peta tačka u nizu merenja, nad stanjem posle koraka 4c (commit `967999f`): isti server,
jednom nad H2, a jednom nad pravim PostgreSQL-om 18.6. Uz to su ovde i rezultati
negativnog prolaza, merenja koje pokazuje šta bi se desilo bez jedinstvenog indeksa.
Poređenje sa [krajem faze 3](../faza-3/README.md) i ranijim tačkama.

Datum merenja: 18. novembar 2026.

---

## 1. Sažetak

**Jedinstveni indeks radi isto na obe baze; naivna provera-pa-upis ne radi ni na jednoj.**
U čistoj trci, kad 50 zahteva za isti termin krene istovremeno, naivna implementacija
bez indeksa propušta duple rezervacije u svih 20 rundi i na H2 i na PostgreSQL-u. Sa
indeksom u svih 80 rundi prolazi tačno jedan zahtev.

**Nad PostgreSQL-om server obrađuje oko 35 puta manje zahteva nego nad H2, i to nije
posledica PostgreSQL-a, nego servera.** Server nema pool veza, pa za svaki zahtev otvara
novu vezu sa bazom. Otvaranje veze sa PostgreSQL-om na ovoj mašini traje 41,5 ms, a sam
upit 0,29 ms. Kod H2, koji radi u istom procesu kao server, otvaranje veze skoro ništa
ne košta, pa se problem do sada nije video.

| | H2 | PostgreSQL 18.6 |
|---|---|---|
| Hladan start (do prvog odgovora) | 2 300 ms | 2 767 ms |
| Topao start | 1 767 ms | 1 745 ms |
| `GET /api/resources`: zahteva/s, medijana | 3 133–3 295, 2,9–3,0 ms | 88–94, 99–107 ms |
| `GET /availability`: zahteva/s, medijana | 3 230–3 354, 2,8–2,9 ms | 90–91, 102–103 ms |
| `GET /api/bookings/mine`: medijana | 2,9–3,0 ms | 109–112 ms |
| `POST /api/auth/login`: zahteva/s, medijana | 31,2–31,4, 318 ms | 22,7–23,7, 414–434 ms |
| Gomila servera posle GC-a | 17,1–17,2 MB | 14,1 MB |
| Procesi baze, privatna memorija | u procesu servera | 31,7 MB (9 procesa) |
| Upis rezervacija (10 korisnika) | ~2 060 /s | ~85 /s |
| Neuspelih zahteva | 0 | 0 |

Rasponi su iz dva pokretanja po bazi (poglavlje 5). Ukupno je u ovoj sesiji bilo
**933 059 zahteva, nijedan neuspeo**.

**Za hipotezu rada:** sam stek radi nad obe baze bez ijedne izmene aplikativnog koda, a
nad PostgreSQL-om i dalje ima višestruku rezervu za opterećenje iz domena (poglavlje 9).
Merenje je otkrilo nedostatak u serveru, pool veza, koji se rešava izmenom koda, a ne
promenom platforme.

---

## 2. Okruženje

| | |
|---|---|
| Slobodan RAM pred merenje | 6,2 GB od 13,9 GB |
| Pozadinsko opterećenje procesora | ~8 % |
| Napajanje | mrežno, plan „Balanced" |
| Commit | `967999f` |

AMD Ryzen 7 5800H (8/16), Windows 11 Home (build 26300), Temurin JDK 21.0.12.1 bez
dodatnih JVM zastavica, k6 v2.2.0, isto kao u fazi 3. Visual C++ runtime je pre ove
faze ažuriran sa 14.29 na 14.52, jer ga PostgreSQL 18.6 za Windows traži.

**PostgreSQL** je isti embedded-postgres (io.zonky.test 2.2.2) kao u testovima, pokrenut
kao zaseban proces na istoj mašini (`:server:postgresZaMerenje`). Podešavanja, očitana
iz same baze:

| Podešavanje | Vrednost | Napomena |
|---|---|---|
| `server_version` | 18.6 | |
| `fsync` | on | embedded-postgres ga za testove isključuje; za merenje je vraćen na podrazumevano |
| `synchronous_commit` | on | isto |
| `shared_buffers` | 128 MB | podrazumevano |
| `max_connections` | 300 | embedded-postgres; podrazumevano je 100 |

PostgreSQL je radio tokom cele sesije, i dok je merenje išlo nad H2, pa je pozadina
ista za obe baze. Gradle proces koji ga je pokrenuo takođe je radio celo vreme.

---

## 3. Negativni prolaz: TOCTOU bez jedinstvenog indeksa

Program [`NegativniProlaz`](../../../server/src/test/kotlin/rs/ftn/hotdesk/server/NegativniProlaz.kt)
(test kod, `:server:negativniProlaz`) u svakoj rundi pušta 50 istovremenih zahteva za
isti termin od pola sata: jednom kroz naivnu proveru-pa-upis nad tabelom **bez**
jedinstvenog indeksa, a jednom nad pravom šemom, kao kontrolu. 20 rundi po varijanti.
`BookingService.kt` i `Tables.kt` se pri tome ne menjaju.

- **Bez barijere:** kako server radi danas. Svaka transakcija otvara novu vezu, pa
  zahtevi stižu do provere razvučeni u vremenu.
- **Sa barijerom:** svaki zahtev otvori transakciju i pročita resurs i korisnika, pa
  čeka da svih 50 budu spremni. To je čista trka, pod istim uslovima na obe baze.

| | H2 | PostgreSQL 18.6 |
|---|---|---|
| Izolacija transakcija (očitana) | READ COMMITTED | READ COMMITTED |
| Bez barijere: rundi sa duplom rezervacijom | **20 / 20** | **11 / 20** |
| Bez barijere: prihvaćeno od 50 (min / medijana / max) | 4 / 11 / 50 | 1 / 2 / 4 |
| Sa barijerom: rundi sa duplom rezervacijom | **20 / 20** | **20 / 20** |
| Sa barijerom: prihvaćeno od 50 (min / medijana / max) | 7 / 14 / 17 | 7 / 13 / 39 |
| Sa indeksom, obe varijante | tačno 1 u svakoj rundi | tačno 1 u svakoj rundi |

Sirovi podaci: [`negativni-prolaz/`](negativni-prolaz/)

**Tumačenje.** Bez barijere PostgreSQL izgleda bezbednije, ali samo zato što je otvaranje
veze sporo (poglavlje 6) i razvlači zahteve, pa se ređe poklope između provere i upisa.
Taj broj je i nestabilan: u prvom pokretanju ovog merenja bile su 3 runde od 20, u
ovom 11. U čistoj trci obe baze propuštaju duple rezervacije u svakoj rundi. Tvrdnja iz
README-a da naivna implementacija „pada" sada ima broj iza sebe, na obe baze.

**Usput izmereno: Exposed ponavlja transakciju.** Posle `SQLException`-a Exposed
automatski ponavlja ceo blok transakcije, podrazumevano do tri pokušaja
(`DatabaseConfig.defaultMaxAttempts = 3`, provereno u bajt-kodu Exposed-a 1.0.0). U
kontroli sa barijerom to se vidi u broju: **148 transakcija** za 50 zahteva, odnosno
1 uspešna i 49 odbijenih × 3. Isto važi za `BookingService`: zahtev za zauzet termin se
izvršava tri puta pre nego što server vrati `409`. Rezultat je tačan, ali baza pod
sukobom radi trostruki posao.

---

## 4. Vreme podizanja

Medijane; hladan n = 3, topao n = 5.

| | H2 | PostgreSQL |
|---|---|---|
| Hladan (ukupno) | 2 300 ms | 2 767 ms |
| Hladan (Ktor) | 1 373 ms | 1 407 ms |
| Topao (ukupno) | 1 767 ms | 1 745 ms |
| Topao (Ktor) | 871 ms | 776 ms |

Sirovi podaci: [`startup-h2.csv`](startup-h2.csv), [`startup-postgres.csv`](startup-postgres.csv)

Hladan start nad PostgreSQL-om je duži za oko 0,47 s. Hladni startovi nad PostgreSQL-om
su bili 2 276, 2 785 i 2 767 ms: prvi je jednak H2, a uzrok te razlike nije utvrđen.
Razlika se skoro ne vidi u Ktor-ovom zapisu podizanja (1 373 naspram 1 407 ms), pa
nastaje van onoga što Ktor meri; gde tačno, nije utvrđeno.

---

## 5. Odziv i propusnost

10 virtuelnih korisnika, 30 s po ruti, posle zagrevanja od 15 s. Redosled pokretanja
H2, PostgreSQL, PostgreSQL, H2, kao kontrolna tačka u fazi 3. Vremena u ms.

| Ruta | Pokretanje | Zahteva | Zahteva/s | med | p90 | p95 | p99 | max |
|---|---|---|---|---|---|---|---|---|
| `/api/resources` | 1 (H2) | 98 852 | 3 294,7 | 2,87 | 3,57 | 3,84 | 4,47 | 42,49 |
| | 2 (PG) | 2 826 | 94,0 | 98,85 | 123,19 | 192,63 | 209,75 | 255,04 |
| | 3 (PG) | 2 644 | 87,7 | 107,33 | 126,27 | 177,76 | 218,85 | 255,50 |
| | 4 (H2) | 93 998 | 3 133,1 | 3,03 | 3,76 | 4,04 | 4,64 | 8,57 |
| `/availability` | 1 (H2) | 100 613 | 3 353,6 | 2,80 | 3,50 | 3,79 | 4,69 | 33,82 |
| | 2 (PG) | 2 732 | 90,8 | 102,35 | 126,73 | 192,58 | 214,56 | 248,06 |
| | 3 (PG) | 2 715 | 90,2 | 103,40 | 127,11 | 191,34 | 213,41 | 310,12 |
| | 4 (H2) | 96 911 | 3 230,0 | 2,90 | 3,65 | 3,95 | 4,99 | 32,79 |
| `/api/bookings/mine` | 1 (H2) | 95 147 | 3 171,3 | 2,93 | 3,75 | 4,14 | 5,73 | 35,03 |
| | 2 (PG) | 2 590 | 85,9 | 108,83 | 132,09 | 198,19 | 220,77 | 241,32 |
| | 3 (PG) | 2 544 | 84,5 | 111,51 | 138,22 | 161,67 | 218,46 | 256,11 |
| | 4 (H2) | 92 038 | 3 067,7 | 3,04 | 3,86 | 4,28 | 5,93 | 25,03 |
| `/api/auth/login` | 1 (H2) | 950 | 31,4 | 317,60 | 335,20 | 339,39 | 347,16 | 352,49 |
| | 2 (PG) | 692 | 22,7 | 433,67 | 453,35 | 472,70 | 540,98 | 559,58 |
| | 3 (PG) | 718 | 23,7 | 414,42 | 462,47 | 484,16 | 530,71 | 564,50 |
| | 4 (H2) | 945 | 31,2 | 318,62 | 338,37 | 342,47 | 352,21 | 359,53 |

Sirovi podaci: [`pokretanja/`](pokretanja/)

Oba pokretanja nad istom bazom se slažu, i redosled ne menja sliku: razlika između baza
je stabilna, a ne posledica stanja mašine. Prijava je nad PostgreSQL-om sporija za oko
25 %; i ona čita korisnika iz baze, ali njeno trajanje uglavnom određuje BCrypt, pa je
razlika manja nego kod ostalih ruta.

---

## 6. Uzrok: cena otvaranja veze

Server se sa bazom povezuje sa `Database.connect(url, ...)`, bez pool-a veza, pa svaka
transakcija otvara novu JDBC vezu i zatvara je na kraju. Cena toga je izmerena
direktno, kratkim programom nad istim JDBC drajverom
([`veza/veza.jsh`](veza/veza.jsh), 30 merenja posle 5 za zagrevanje):

| | Medijana |
|---|---|
| Otvaranje nove veze sa PostgreSQL-om | **41,52 ms** (min 40,18, max 154,62) |
| Upit na tek otvorenoj vezi | 2,89 ms |
| Isti upit na već otvorenoj vezi | **0,29 ms** |

Sirovi podaci: [`veza/rezultat.txt`](veza/rezultat.txt)

PostgreSQL za svaku vezu pokreće poseban proces, a na Windows-u je pokretanje procesa
skupo. Zahtev nad PostgreSQL-om je zato skoro ceo otvaranje veze: jedan administrator,
sam, čeka oko 50 ms i za pregled od samo 100 rezervacija (poglavlje 8), a pod 10
istovremenih korisnika vreme raste na oko 100 ms. H2 radi u istom procesu kao server,
pa nova veza ne košta skoro ništa i ovaj problem se u ranijim merenjima nije mogao videti.

**Posledica.** Pool veza, koji drži otvorene veze i daje ih transakcijama na korišćenje,
uklonio bi ovaj trošak; očekivano je da bi zahtevi nad PostgreSQL-om tada bili u
redu veličine onih nad H2. To je predviđanje iz ovih brojeva, a **ne merenje**: pool
veza u ovoj fazi nije uveden.

---

## 7. Memorija

Posle prisilnog sakupljanja smeća, na kraju pokretanja:

| | H2 (1 / 4) | PostgreSQL (2 / 3) |
|---|---|---|
| Gomila servera | 17,2 / 17,1 MB | 14,1 / 14,1 MB |
| Privatna memorija servera | 320,1 / 306,4 MB | 267,2 / 270,7 MB |
| Procesi baze: broj, privatna memorija | u procesu servera | 9, 31,7 / 31,7 MB |
| Procesi baze: zbir RSS-a | | 176,2 / 222,1 MB ¹ |

Sirovi podaci: `memory.csv` u [`pokretanja/`](pokretanja/)

¹ Zbir RSS-a precenjuje, jer se deljena memorija baze (`shared_buffers`) računa u svakom
procesu koji je koristi; privatna memorija je pouzdaniji zbir.

Nad PostgreSQL-om server drži oko 3 MB manje, jer podaci nisu u njegovoj JVM; baza zato
ima svojih oko 32 MB. Ukupno je približno isto. Gomila servera nad PostgreSQL-om ostaje
na 50 MB rezervisanog prostora i pod opterećenjem, jer server obrađuje 35 puta manje
zahteva i stvara manje smeća.

---

## 8. Nove rute i globalni pregled nad obe baze

Isto merenje kao u fazi 3, na svežem serveru, jednom nad svakom bazom.

| | H2 | PostgreSQL |
|---|---|---|
| `/api/resources`: zahteva/s, medijana | 3 300,7, 2,88 ms | 97,2, 95,65 ms |
| `?amenity=projektor`: zahteva/s, medijana | 3 183,8, 2,96 ms | 91,8, 101,18 ms |

Globalni pregled (`GET /api/bookings`), jedan administrator (1 VU) i 10 korisnika:

| Rezervacija | Odgovor | H2: 1 VU med | H2: 10 VU zahteva/s, med | PG: 1 VU med | PG: 10 VU zahteva/s, med |
|---|---|---|---|---|---|
| 100 | 26,3 KB | 2,21 ms | 2 837,3, 3,36 ms | 49,78 ms | 91,4, 101,56 ms |
| 1 000 | 262,8 KB | 4,66 ms | 1 017,4, 9,42 ms | 53,77 ms | 82,3, 114,25 ms |
| 10 000 | 2 627,7 KB | 31,12 ms | 102,3, 96,14 ms | 103,80 ms | 37,5, 258,79 ms |

Upis rezervacija pri punjenju baze (10 korisnika): H2 205, 1 064 i 2 056 upisa u sekundi
po fazi punjenja, PostgreSQL 55, 83 i 85. Kod PostgreSQL-a upis plaća i otvaranje veze i
`fsync` pri potvrdi transakcije.

Sirovi podaci: [`novo/`](novo/)

Nad PostgreSQL-om globalni pregled raste sporije u relativnom smislu (50 → 104 ms, jer
je otvaranje veze stalan deo), ali je u apsolutnom uvek sporiji od H2. Zaključak iz
faze 3 važi i ovde: za stvarnu upotrebu pregled traži ograničenje po periodu ili
straničenje.

---

## 9. Da li je PostgreSQL varijanta dovoljna?

Uz iste pretpostavke kao u [fazi 3](../faza-3/README.md#7-da-li-je-to-dovoljno-zahtevi-iz-domena)
(firma od 1 000 zaposlenih, svi u istih 15 minuta, šest zahteva po zaposlenom):

| | Potrebno | PostgreSQL, bez pool-a veza | Rezerva |
|---|---|---|---|
| Svi zahtevi | ≈ 6,7 zahteva/s | ~85–97 čitanja/s | ~13 puta |
| Prijava | ≈ 1,1 prijava/s | ~23 prijave/s | ~20 puta |
| Prijava, svi u istom minutu | ≈ 16,7 prijava/s | ~23 prijave/s | ~1,4 puta |

Odziv nad PostgreSQL-om, na serveru: jedan korisnik ~50 ms, deset istovremenih ~100 ms
medijane i ~220 ms p99. To je oko granice od 0,1 s do koje korisnik doživljava odgovor
kao trenutan, i daleko ispod granice od 1 s (Nielsen, *Usability Engineering*, 1993).

**Zaključak.** I bez pool-a veza PostgreSQL varijanta zadovoljava opterećenje iz domena,
ali sa rezervom od oko 13 puta umesto 400, i sa odzivom oko granice trenutnog. Pool veza
je zato prva izmena koju ovo merenje predlaže.

---

## 10. Ograničenja

Ograničenja iz ranijih merenja i dalje važe. Uz njih:

- **PostgreSQL radi na istoj mašini kao server**, bez mreže između njih. U stvarnoj
  upotrebi baza je obično na drugoj mašini, pa bi i otvaranje veze i svaki upit
  plaćali i mrežu.
- **Cena otvaranja veze merena je samo na Windows-u.** Na Linux-u PostgreSQL pravi
  proces za vezu jeftinije; koliko, ovde nije mereno.
- **Pool veza nije meren.** Očekivani efekat u poglavlju 6 je predviđanje, ne rezultat.
- **Trajnost upisa kod H2 nije posebno podešavana ni merena**, pa poređenje brzine upisa
  između baza nije poređenje istog nivoa trajnosti.
- **Uzrok bržeg prvog hladnog starta nad PostgreSQL-om** (2 276 naspram ~2 770 ms)
  nije utvrđen.
- **Negativni prolaz je merenje sa 20 rundi po varijanti**; broj rundi sa duplom
  rezervacijom bez barijere na PostgreSQL-u je nestabilan između pokretanja (3 i 11).

---

## 11. Kako ponoviti

1. `.\gradlew.bat :server:postgresZaMerenje` pokreće PostgreSQL 18.6 na portu 54320, sa
   praznim bazama `hotdesk_1` … `hotdesk_10`, i ispisuje podešavanja.
2. Skripte u [`skripte/`](skripte/) su skripte faze 3 sa izborom baze: bez parametra
   rade nad H2, a sa `-DbUrl` (`-DbUrls` za vreme podizanja) nad PostgreSQL-om, preko
   promenljivih `DB_*` i bez izmene servera. Korišćene baze: `hotdesk_1`–`4` za vreme
   podizanja, `hotdesk_5` i `6` za pokretanja 2 i 3, `hotdesk_7` za nove rute.
3. PostgreSQL se na kraju gasi sa `pg_ctl stop -D <direktorijum podataka> -m fast`;
   zaustavljanje Gradle zadatka ga ne gasi.
4. Negativni prolaz: `.\gradlew.bat :server:negativniProlaz`, uz `-Pbaza=postgres` za
   PostgreSQL. Cena veze: `jshell` sa PostgreSQL JDBC drajverom i
   [`veza/veza.jsh`](veza/veza.jsh), dok PostgreSQL za merenje radi.
