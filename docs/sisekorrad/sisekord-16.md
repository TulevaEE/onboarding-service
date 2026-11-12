# Sisekord 16: Täiendava Kogumisfondi Osakute registri pidamise sisekord – reeglid

Reeglid järgivad sisekorra versiooni 1.1, mis kehtib alates 31.08.2026.

Tekst on genereeritud reeglite koodist ja iga näide on automaattest. Faili käsitsi ei muudeta.

## Kaetus

Kood rakendab järgmisi sisekorra punkte; ülejäänud punktid on sisekorra tekstis.

- p 1.1.3 – Märkimis- ja tagasivõtmiskorralduse tehingupäev
- p 1.2.4 – Sissemakse vastuvõtmine või tagastamine maksjale (bounce-back)
- p 1.4.1–1.4.2 – Igaöine pangakontode ja registri rahakontode võrdlus

## p 1.1.3 – Märkimis- ja tagasivõtmiskorralduse tehingupäev

Tulemus on esimene, mille tingimus kehtib:

- tehing tehakse järgmisel tööpäeval, kui korraldus antakse tööpäeval ja korraldus antakse enne kella 16:00;
- tehing tehakse ülejärgmisel tööpäeval, kui korraldus antakse laupäeval, pühapäeval või riigipühal või korraldus antakse kell 16:00 või hiljem.

Andmed: aeg on Eesti aeg; tööpäev ei ole laupäev, pühapäev ega Eesti riigipüha; korraldust saab tagasi võtta kuni tehingule eelneva tööpäeva kella 16:00ni.

Näide: tööpäeval kell 15:59 antud korraldus – tehing tehakse järgmisel tööpäeval, sest korraldus antakse tööpäeval ja korraldus antakse enne kella 16:00.

Näide: tööpäeval kell 16:00 antud korraldus – tehing tehakse ülejärgmisel tööpäeval, sest korraldus antakse kell 16:00 või hiljem.

Näide: laupäeva hommikul antud korraldus – tehing tehakse ülejärgmisel tööpäeval, sest korraldus antakse laupäeval, pühapäeval või riigipühal.

## p 1.2.4 – Sissemakse vastuvõtmine või tagastamine maksjale (bounce-back)

Tulemus on esimene, mille tingimus kehtib:

- makse tagastatakse, kui maksest ei leita kehtivat isikukoodi ega registrikoodi;
- makse tagastatakse, kui maksja on ettevõte, kes maksab teise isiku kontole;
- makse tagastatakse, kui maksekirjelduses ei ole piiratud teovõimega isiku isikukoodi ja maksja on keegi muu kui osakuomanik või tema eestkostja;
- makse tagastatakse, kui osakuomanikku ei leita Tuleva kliendina;
- makse tagastatakse, kui maksekirjelduses ei ole piiratud teovõimega isiku isikukoodi, pank ei edasta maksja koodi ja maksja nimi ei klapi osakuomaniku nimega;
- makse tagastatakse, kui osakuomanik ei ole täiendava kogumisfondiga liitunud;
- makse võetakse vastu ja kantakse osakuomaniku kontole, kui maksest leitakse kehtiv isikukood või registrikood, maksja ei ole teise isiku kontole maksev ettevõte, (maksekirjelduses on piiratud teovõimega isiku isikukood või maksja on osakuomanik ise või tema eestkostja või pank maksja koodi ei edasta), osakuomanik on Tuleva klient, (maksekirjelduses on piiratud teovõimega isiku isikukood, pank edastab maksja koodi või maksja nimi klapib osakuomaniku nimega) ja osakuomanik on täiendava kogumisfondiga liitunud.

Andmed: kood otsitakse esmalt maksekirjeldusest, selle puudumisel võetakse pangalt saadud maksja kood; registrikoodiga saavad sissemakseid teha ka ettevõtted; kui pank maksja koodi ei edasta, loetakse maksja ettevõtteks nime järgi; teovõimet vaadatakse raha laekumise päeva seisuga; piiratud teovõimega isiku kontole võib maksta igaüks; nime erinevus loeb ainult siis, kui pank maksja koodi ei edasta; liitumine on lõpetatud, kui osakuomanik on läbinud Tuleva hoolsusmeetmed; sissemakse vastuvõtmisel sanktsiooni- ega PEP-kontrolli ei korrata.

Näide: maksekirjelduses ega maksja andmetes ei ole kehtivat koodi – makse tagastatakse, sest maksest ei leita kehtivat isikukoodi ega registrikoodi.

Näide: ettevõte maksab töötaja kontole – makse tagastatakse, sest maksest leitakse kehtiv isikukood või registrikood ja maksja on ettevõte, kes maksab teise isiku kontole.

Näide: koodita maksja, kelle nimi on ettevõtte nimi, maksab isiku kontole – makse tagastatakse, sest maksest leitakse kehtiv isikukood või registrikood ja maksja on ettevõte, kes maksab teise isiku kontole.

Näide: teine isik maksab täisealise osakuomaniku kontole – makse tagastatakse, sest maksest leitakse kehtiv isikukood või registrikood, maksja ei ole teise isiku kontole maksev ettevõte, maksekirjelduses ei ole piiratud teovõimega isiku isikukoodi ja maksja on keegi muu kui osakuomanik või tema eestkostja.

Näide: vanavanem maksab alaealise lapselapse kontole – makse võetakse vastu ja kantakse osakuomaniku kontole, sest maksest leitakse kehtiv isikukood või registrikood, maksja ei ole teise isiku kontole maksev ettevõte, maksekirjelduses on piiratud teovõimega isiku isikukood, osakuomanik on Tuleva klient, pank edastab maksja koodi ja osakuomanik on täiendava kogumisfondiga liitunud.

Näide: eestkostja maksab hoolealuse kontole – makse võetakse vastu ja kantakse osakuomaniku kontole, sest maksest leitakse kehtiv isikukood või registrikood, maksja ei ole teise isiku kontole maksev ettevõte, maksekirjelduses on piiratud teovõimega isiku isikukood, maksja on osakuomanik ise või tema eestkostja või pank maksja koodi ei edasta, osakuomanik on Tuleva klient, pank edastab maksja koodi ja osakuomanik on täiendava kogumisfondiga liitunud.

Näide: osakuomanikku ei leita Tuleva kliendina – makse tagastatakse, sest maksest leitakse kehtiv isikukood või registrikood, maksja ei ole teise isiku kontole maksev ettevõte, maksja on osakuomanik ise või tema eestkostja või pank maksja koodi ei edasta ja osakuomanikku ei leita Tuleva kliendina.

Näide: pank ei edasta maksja koodi ja nimi ei klapi – makse tagastatakse, sest maksest leitakse kehtiv isikukood või registrikood, maksja ei ole teise isiku kontole maksev ettevõte, maksja on osakuomanik ise või tema eestkostja või pank maksja koodi ei edasta, osakuomanik on Tuleva klient, maksekirjelduses ei ole piiratud teovõimega isiku isikukoodi, pank ei edasta maksja koodi ja maksja nimi ei klapi osakuomaniku nimega.

Näide: pank ei edasta maksja koodi, nimi klapib – makse võetakse vastu ja kantakse osakuomaniku kontole, sest maksest leitakse kehtiv isikukood või registrikood, maksja ei ole teise isiku kontole maksev ettevõte, maksja on osakuomanik ise või tema eestkostja või pank maksja koodi ei edasta, osakuomanik on Tuleva klient, maksja nimi klapib osakuomaniku nimega ja osakuomanik on täiendava kogumisfondiga liitunud.

Näide: pank edastab osakuomaniku koodi, nimi ei klapi – makse võetakse vastu ja kantakse osakuomaniku kontole, sest maksest leitakse kehtiv isikukood või registrikood, maksja ei ole teise isiku kontole maksev ettevõte, maksja on osakuomanik ise või tema eestkostja või pank maksja koodi ei edasta, osakuomanik on Tuleva klient, pank edastab maksja koodi ja osakuomanik on täiendava kogumisfondiga liitunud.

Näide: osakuomanik maksab ise, kuid ei ole fondiga liitunud – makse tagastatakse, sest maksest leitakse kehtiv isikukood või registrikood, maksja ei ole teise isiku kontole maksev ettevõte, maksja on osakuomanik ise või tema eestkostja või pank maksja koodi ei edasta, osakuomanik on Tuleva klient, pank edastab maksja koodi, maksja nimi klapib osakuomaniku nimega ja osakuomanik ei ole täiendava kogumisfondiga liitunud.

Näide: osakuomanik maksab ise oma pangakontolt – makse võetakse vastu ja kantakse osakuomaniku kontole, sest maksest leitakse kehtiv isikukood või registrikood, maksja ei ole teise isiku kontole maksev ettevõte, maksja on osakuomanik ise või tema eestkostja või pank maksja koodi ei edasta, osakuomanik on Tuleva klient, pank edastab maksja koodi, maksja nimi klapib osakuomaniku nimega ja osakuomanik on täiendava kogumisfondiga liitunud.

## p 1.4.1–1.4.2 – Igaöine pangakontode ja registri rahakontode võrdlus

Tulemus on esimene, mille tingimus kehtib:

- viga: saldod ei klapi, kui panga lõppsaldo erineb registri rahakonto saldost päeva lõpus;
- viga: võrdlus on blokeeritud, kui registris on enne päeva lõppu lahendamata klassifitseerimata pangakandeid;
- kontroll on korras, kui panga lõppsaldo võrdub registri rahakonto saldoga päeva lõpus ja registris ei ole lahendamata klassifitseerimata pangakandeid.

Andmed: kontrollitakse kogumis-, väljamakse- ja fondikontot; kontroll käivitub, kui eelmise päeva väljavõte on pangast tõmmatud; väljavõtteid tõmmatakse iga 30 minuti järel kella 04:00–23:30; vea korral saadetakse teade Slacki ja kontroll lõpeb veaga; klassifitseerimata kandeid kontrollitakse alles siis, kui saldod klapivad.

Näide: saldod erinevad – viga: saldod ei klapi, sest panga lõppsaldo erineb registri rahakonto saldost päeva lõpus.

Näide: saldod klapivad, üks kanne on klassifitseerimata – viga: võrdlus on blokeeritud, sest panga lõppsaldo võrdub registri rahakonto saldoga päeva lõpus ja registris on enne päeva lõppu lahendamata klassifitseerimata pangakandeid.

Näide: saldod klapivad, kõik kanded on klassifitseeritud – kontroll on korras, sest panga lõppsaldo võrdub registri rahakonto saldoga päeva lõpus ja registris ei ole lahendamata klassifitseerimata pangakandeid.
