# Merenja: kraj faze 3

Četvrta tačka u nizu merenja, nad stanjem posle koraka 3c (commit `9d3f81d`):
pravila za resurs (`ResourceRules`) na serveru, filter po opremi, upravljanje
resursima i globalni pregled rezervacija u aplikaciji. Poređenje sa
[krajem faze 2](../faza-2-kraj/README.md) i ranijim tačkama.

Datum merenja: 10. novembar 2026.

**Novo u metodologiji: kontrolna tačka.** Pre ovog merenja okruženje je promenjeno
(uklonjeni su nepotrebni programi i servisi Windows-a). Da bi se razlika zbog
okruženja odvojila od razlike zbog koda, u istoj sesiji je izmeren i JAR sa oznake
`faza-2` (commit `7fa639b`), redosledom faza 2, faza 3, faza 3, faza 2.

---

## 1. Sažetak

**Faza 3 nema merljiv trošak na postojećim rutama.** Kad se izmere jedna za drugom
u istom stanju mašine, faza 3 i kontrolna faza 2 se razlikuju za manje od 1 % na
svim rutama. Vreme podizanja i memorija posle sakupljanja smeća su isti.

| | Faza 1 | Posle autentikacije | Kraj faze 2 | **Kraj faze 3** ¹ |
|---|---|---|---|---|
| Hladan start (do prvog odgovora) | 1 778 ms | 2 292 ms | 2 316 ms | **2 287 ms** |
| Topao start | 1 764 ms | 1 761 ms | 1 770 ms | **1 744 ms** |
| RSS u mirovanju | 172,2 MB | 161,4 MB | 154,3 MB | **153,3 / 161,9 MB** |
| Gomila stvarno zauzeta posle GC-a | 15,1 MB | 17,0 MB | 17,0 MB | **17,1 / 17,2 MB** |
| `GET /api/resources`: zahteva/s | 3 250 | 3 326 | 3 305 | **2 797 / 2 790** ² |
| `GET /api/resources`: medijana / p99 | 2,90 / 4,58 ms | 2,85 / 4,37 ms | 2,84 / 4,49 ms | **3,44 / 4,96 ms** |
| `GET /availability`: zahteva/s | 3 161 | 3 312 | 3 317 | **2 548 / 2 798** ² |
| `GET /api/bookings/mine`: medijana / p99 | ruta nije postojala | 3,40 / 6,49 ms | 3,29 / 6,18 ms | **3,54 / 6,92 ms** |
| `POST /api/auth/login`: zahteva/s / medijana | ruta nije postojala | 23,1 / 427 ms | 22,6 / 440 ms | **25,0 / 399 ms** |
| Samostalni JAR | 20,5 MB | 27,3 MB | 27,3 MB | **27,3 MB** (+10,7 KB) |
| Neuspelih zahteva | 0 / 192 365 | 0 / 281 562 | 0 / 283 655 | **0 / 1 276 806** ³ |

¹ Dva pokretanja faze 3 u istoj sesiji; gde stoji jedan broj, uzeto je pokretanje 3,
izmereno neposredno pre kontrolnog (poglavlje 3).
² Niže nego u ranijim tačkama, ali **jednako za obe verzije**: kontrolni JAR faze 2
u istoj sesiji daje 2 787 i 2 793 zahteva/s. Pad je posledica okruženja, ne koda.
³ Sva četiri pokretanja i merenja novih ruta (poglavlje 6) zajedno.

Nalazi o novim rutama:

- **Filter po opremi** (`?amenity=`) smanjuje propusnost liste resursa za 1,4 %;
  medijana je ista.
- **Globalni pregled bez straničenja raste linearno** sa brojem rezervacija: za
  jednog administratora 2,2 ms pri 100, 4,5 ms pri 1 000 i 32 ms pri 10 000
  rezervacija, uz odgovor od 26 KB, 263 KB i 2,6 MB. Firma od 1 000 zaposlenih bi
  10 000 rezervacija imala posle dve radne nedelje (poglavlje 6.2).
- **Zahtevi iz domena:** vršno opterećenje firme od 1 000 zaposlenih procenjeno je na
  oko 7 zahteva u sekundi, naspram oko 2 800 koliko server obrađuje (poglavlje 7).

---

## 2. Okruženje

| | Faza 1 | Posle autentikacije | Kraj faze 2 | Kraj faze 3 |
|---|---|---|---|---|
| Slobodan RAM pred merenje | 6,2 GB | 7,0 GB | 5,5 GB | 6,9 GB |
| Pozadinsko opterećenje procesora | ~12 % | ~10 % | ~10 % | ~8 % |
| Napajanje | mrežno | mrežno | mrežno | mrežno, plan „Balanced" |
| Commit | `8ed000a` | `2026226` | `60daee4` | `9d3f81d` (+ kontrolni `7fa639b`) |

AMD Ryzen 7 5800H (8/16), 13,9 GB RAM, Windows 11 Home (build 26300), Temurin
JDK 21.0.12.1 bez dodatnih JVM zastavica, k6 v2.2.0. Svaki hladni start u novom,
praznom folderu.

Razlike u odnosu na ranija merenja:

- Pre merenja su iz Windows-a uklonjeni nepotrebni programi i servisi.
- Gradle daemoni zaostali iz build-a zaustavljeni su pre merenja (oslobodilo je
  2,3 GB RAM-a). U ranijim merenjima to nije beleženo.

---

## 3. Kontrolna tačka i redosled

Isto merenje odziva (zagrevanje, pa po 30 s na svakoj ruti) pušteno je četiri puta,
jedno za drugim. Propusnost u zahtevima u sekundi:

| Pokretanje | Verzija | `resources` | `availability` | `mine` | `login` |
|---|---|---|---|---|---|
| 1 | faza 2 (kontrola) | 3 205 | 3 234 | 2 988 | 26,3 |
| 2 | faza 3 | 2 797 | 2 548 | 2 385 | 23,4 |
| 3 | faza 3 | 2 790 | 2 798 | 2 651 | 25,0 |
| 4 | faza 2 (kontrola) | 2 787 | 2 793 | 2 631 | 22,5 |

Sirovi podaci: [`pokretanja/`](pokretanja/)

**Tumačenje.** Pokretanja 3 i 4 su izmerena neposredno jedno za drugim i razlikuju
se za manje od 1 % na čitanjima. To je poređenje verzija u istom stanju mašine, i
ono ne pokazuje nikakav trošak faze 3.

Između pokretanja 1 i 2 razlika je 11–21 %, i to **i na prijavi**. Kod prijave nije
menjan u fazi 3, a njeno trajanje određuje BCrypt, dakle čist rad procesora. Razlika
je zato posledica stanja mašine, a ne koda. Prvo pokretanje u sesiji je najbrže i
blizu ranijih tačaka; posle njega je mašina radila sporije, za obe verzije.

Najverovatniji uzrok je zagrevanje procesora pod višeminutnim opterećenjem, ali to
nije potvrđeno: temperatura se bez administratorskih prava ne može očitati, a takt
koji Windows prijavljuje (3 201 MHz) je nominalni i ne pokazuje stvarno ubrzavanje
procesora.

**Posledica za čitanje rezultata:** verzije se smeju porediti samo unutar iste
sesije. U poređenju sa krajem faze 2, propusnost liste resursa i mreže slotova je u
ovoj sesiji za obe verzije oko 16 % niža, `mine` oko 6 %, a prijava je čak nešto
brža. Okruženje, dakle, nije pomerilo sve rute jednako.

---

## 4. Vreme podizanja

Medijane; hladan n = 3, topao n = 5.

| | Kraj faze 2 (ranije) | Faza 2, kontrola | Faza 3 |
|---|---|---|---|
| Hladan (ukupno) | 2 316 ms | 2 296 ms | 2 287 ms |
| Hladan (Ktor) | 1 346 ms | 1 324 ms | 1 357 ms |
| Topao (ukupno) | 1 770 ms | 1 760 ms | 1 744 ms |
| Topao (Ktor) | 830 ms | 804 ms | 802 ms |

Sirovi podaci: [`startup-faza3.csv`](startup-faza3.csv),
[`startup-faza2-kontrola.csv`](startup-faza2-kontrola.csv)

Podizanje se nije promenilo ni zbog koda ni zbog okruženja: kontrolna faza 2 daje
iste vrednosti kao merenje sa kraja faze 2, pre promene okruženja.

---

## 5. Odziv i propusnost (faza 3)

10 virtuelnih korisnika, 30 s po ruti, posle zagrevanja od 15 s. Vremena u ms.

| Ruta | Pokretanje | Zahteva | Zahteva/s | med | p90 | p95 | p99 | max |
|---|---|---|---|---|---|---|---|---|
| `/api/resources` | 2 | 83 914 | 2 796,9 | 3,41 | 4,15 | 4,42 | 5,09 | 9,15 |
| | 3 | 83 715 | 2 790,3 | 3,44 | 4,10 | 4,35 | 4,96 | 7,93 |
| `/api/resources/{id}/availability` | 2 | 76 450 | 2 548,1 | 3,71 | 4,47 | 4,84 | 6,12 | 27,36 |
| | 3 | 83 937 | 2 797,7 | 3,38 | 4,07 | 4,42 | 5,76 | 27,00 |
| `/api/bookings/mine` (sa tokenom) | 2 | 71 545 | 2 384,6 | 3,91 | 4,92 | 5,45 | 7,25 | 37,42 |
| | 3 | 79 544 | 2 651,2 | 3,54 | 4,33 | 4,77 | 6,92 | 24,37 |
| `/api/auth/login` | 2 | 710 | 23,4 | 424,22 | 443,66 | 451,50 | 465,82 | 472,50 |
| | 3 | 759 | 25,0 | 399,05 | 424,37 | 429,25 | 457,85 | 526,01 |

Sirovi podaci: `latency.csv`, `memory.csv` i `summary-*.json` u
[`pokretanja/2-faza3`](pokretanja/2-faza3/) i [`pokretanja/3-faza3`](pokretanja/3-faza3/)

Memorija posle prisilnog sakupljanja smeća: **17,1 i 17,2 MB** za fazu 3, **17,2 i
17,0 MB** za kontrolnu fazu 2. Nova pravila i rute nisu povećali stvarno zauzetu
memoriju.

---

## 6. Nove rute

### 6.1 Filter po opremi

Na svežem serveru, u istom pokretanju; 10 virtuelnih korisnika, 30 s.

| Ruta | Zahteva/s | med | p99 | max |
|---|---|---|---|---|
| `/api/resources` | 2 625,9 | 3,65 | 5,27 | 15,60 |
| `/api/resources?amenity=projektor` | 2 589,1 | 3,67 | 5,91 | 23,65 |

Sirovi podaci: [`novo/`](novo/)

Filter dodaje podupit nad tabelom opreme, a vraća manje resursa (2 od 7). Ta dva
efekta se skoro poništavaju: propusnost je niža za 1,4 %, medijana je ista.

### 6.2 Globalni pregled bez straničenja

`GET /api/bookings` vraća sve rezervacije, i istoriju, bez straničenja; to je
prihvaćeno ograničenje faze 3. Ovde je pretvoreno u broj. Baza je punjena preko
API-ja, rezervacijama od pola sata (k6, [`seed-bookings.js`](skripte/seed-bookings.js));
svih 10 000 je upisano bez ijednog odbijanja.

| Rezervacija | Veličina odgovora | Gomila posle GC-a | 1 administrator: med / p99 | 10 korisnika: zahteva/s, med / p99 |
|---|---|---|---|---|
| 100 | 26,3 KB | 17,2 MB | 2,16 / 3,89 ms | 2 285,5; 4,18 / 6,26 ms |
| 1 000 | 262,8 KB | 19,5 MB | 4,54 / 6,02 ms | 904,2; 10,69 / 15,53 ms |
| 10 000 | 2 627,7 KB | 27,7 MB | 32,14 / 64,50 ms | 96,9; 101,66 / 135,98 ms |

Sirovi podaci: [`novo/global.csv`](novo/global.csv) i `summary-bookings-*.json`

**Tumačenje.** Trajanje i veličina odgovora rastu približno linearno, oko 270 bajtova
JSON-a po rezervaciji. Pri 10 000 rezervacija server odgovara jednom administratoru
za 32 ms, što je i dalje brzo; telefon, međutim, preko Wi-Fi mreže preuzima i
parsira 2,6 MB, što ovde nije mereno.

U domenu to brzo postaje problem. Firma od 1 000 zaposlenih, sa jednom rezervacijom
po zaposlenom po radnom danu, ima 10 000 rezervacija posle **dve radne nedelje**, a
oko 250 000 posle godinu dana. Linearnom ekstrapolacijom (ne merenjem) to je odgovor
od oko 65 MB i oko 0,8 s samo na serveru. **Za stvarnu upotrebu pregled mora da
dobije ograničenje po periodu ili straničenje**; za demonstraciju je dovoljan.

---

## 7. Da li je to dovoljno? Zahtevi iz domena

Brojevi sami po sebi ne kažu da li je server dovoljno brz. Ovde su upoređeni sa
procenjenim opterećenjem stvarne upotrebe i sa granicama odziva iz literature o
upotrebljivosti.

### 7.1 Opterećenje

Pretpostavke, namerno nepovoljne:

- firma od **1 000 zaposlenih**;
- svaki zaposleni jednom dnevno: prijava, lista resursa, dva pregleda mreže slotova,
  jedna rezervacija i jedan pregled svojih rezervacija, ukupno **6 zahteva**, od
  kojih je jedan prijava (token važi 24 sata);
- svi to rade u istih **15 minuta** jutarnjeg vrha.

| | Potrebno | Izmereno (niže vrednosti ove sesije) | Rezerva |
|---|---|---|---|
| Svi zahtevi | 6 000 / 900 s ≈ **6,7 zahteva/s** | ~2 500–2 800 čitanja/s | ~400 puta |
| Prijava | 1 000 / 900 s ≈ **1,1 prijava/s** | ~23–25 prijava/s | ~20 puta |
| Prijava, svi u istom minutu | 1 000 / 60 s ≈ **16,7 prijava/s** | ~23–25 prijava/s | ~1,4 puta |

Ekrani sa informacijama na STB uređajima (proširenje rada) dodaju malo: na primer,
20 ekrana koji osvežavaju zauzetost na 30 s čine **0,7 zahteva/s**.

### 7.2 Odziv

Nielsen navodi tri granice odziva: **0,1 s**, do koje korisnik doživljava odgovor kao
trenutan; **1 s**, do koje njegov tok misli ostaje neprekinut iako primećuje kašnjenje;
i **10 s**, granicu zadržavanja pažnje (Nielsen, J., *Usability Engineering*, Academic
Press, 1993).

| Operacija | Na serveru | Granica |
|---|---|---|
| Lista, mreža slotova, moje rezervacije | medijana 3–4 ms | daleko ispod 0,1 s |
| Prijava pod opterećenjem (10 istovremenih) | medijana ~0,4 s | između 0,1 s i 1 s, namerno (BCrypt) |
| Globalni pregled, 10 000 rezervacija | 32 ms | ispod 0,1 s |
| Globalni pregled, godina podataka (ekstrapolacija) | ~0,8 s, uz ~65 MB prenosa | blizu 1 s, bez prenosa |

**Zaključak.** Za firmu od 1 000 zaposlenih server ima višestruku rezervu u
propusnosti, i svi uobičajeni zahtevi su na serveru daleko ispod granice
trenutnog odziva. Jedino globalni pregled, sa rastom istorije, dolazi do granice
upotrebljivosti, i to je posledica izbora bez straničenja, ne platforme.

**Ograde.** Proračun je model, ne merenje: opterećenje je ravnomerno raspoređeno u
15 minuta, bez naleta. Odziv je meren na serveru preko `localhost`-a; mreža između
telefona i servera nije uključena.

---

## 8. Veličina JAR-a

Samostalni JAR je porastao za **10 711 bajtova** (28 659 277 → 28 669 988), bez
ijedne nove zavisnosti: to su `ResourceRules` u modulu `:core`, provera u rutama i
filter po opremi.

JAR faze 2 je za ovo merenje napravljen iznova iz oznake `faza-2` i ima 7 bajtova
više nego u izveštaju sa kraja faze 2 (28 659 270), iz istog koda. Build nije
bajt za bajt ponovljiv (vremena u ZIP zapisu), pa se razlike od nekoliko bajtova ne
tumače.

---

## 9. Ograničenja

Sva ograničenja iz [faze 1](../faza-1/README.md#7-ograničenja),
[merenja posle autentikacije](../faza-2-korak-1/README.md#10-ograničenja) i
[kraja faze 2](../faza-2-kraj/README.md#8-ograničenja) i dalje važe. Uz njih:

- **Okruženje je promenjeno pre ovog merenja.** Apsolutni brojevi zato nisu direktno
  uporedivi sa ranijim tačkama; poređenje verzija važi samo unutar ove sesije, preko
  kontrolne tačke.
- **Uzrok usporenja posle prvog pokretanja nije utvrđen** (poglavlje 3).
- **Izolovan skok pri prijavi:** u pokretanju 4 (kontrola) p99 je 1 014 ms, a max
  1 165 ms, uz medijanu od 429 ms. Nije ponovljen ni istražen.
- **Globalni pregled je meren preko `localhost`-a**, bez prenosa preko mreže i bez
  parsiranja na telefonu.
- **Proračun iz domena je model** sa izričito navedenim pretpostavkama (poglavlje 7).

---

## 10. Kako ponoviti

Skripte su u [`skripte/`](skripte/). Za razliku od prethodnih merenja, `startup4.ps1`,
`load4.ps1` i `novo4.ps1` primaju JAR i izlazni folder kao parametre, pa se ista
skripta pušta nad obe verzije; `latency4.ps1` iz k6 izveštaja pravi `latency.csv`.
Putanje do JDK-a i k6 su i dalje putanje razvojne mašine.

Kontrolni JAR se dobija iz oznake: `git archive -o faza2.tar faza-2`, raspakivanje u
poseban folder i `gradlew :server:buildFatJar` u njemu.
