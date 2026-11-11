# Sisekord 16: Täiendava Kogumisfondi Osakute registri pidamise sisekord – reeglid

Reeglid järgivad sisekorra versiooni 1.1, mis kehtib alates 31.08.2026.

Tekst on genereeritud reeglite koodist ja iga näide on automaattest. Faili käsitsi ei muudeta.

## Kaetus

Kood rakendab järgmisi sisekorra punkte; ülejäänud punktid on sisekorra tekstis.

- p 1.1.3 – Märkimis- ja tagasivõtmiskorralduse tehingupäev
- p 1.4.1–1.4.2 – Igaöine pangakontode ja registri rahakontode võrdlus

## p 1.1.3 – Märkimis- ja tagasivõtmiskorralduse tehingupäev

Tulemus on esimene, mille tingimus kehtib:

- tehing tehakse järgmisel tööpäeval, kui korraldus antakse tööpäeval ja korraldus antakse enne kella 16:00;
- tehing tehakse ülejärgmisel tööpäeval, kui korraldus antakse laupäeval, pühapäeval või riigipühal või korraldus antakse kell 16:00 või hiljem.

Andmed: aeg on Eesti aeg; tööpäev ei ole laupäev, pühapäev ega Eesti riigipüha; korraldust saab tagasi võtta kuni tehingule eelneva tööpäeva kella 16:00ni.

Näide: tööpäeval kell 15:59 antud korraldus – tehing tehakse järgmisel tööpäeval, sest korraldus antakse tööpäeval ja korraldus antakse enne kella 16:00.

Näide: tööpäeval kell 16:00 antud korraldus – tehing tehakse ülejärgmisel tööpäeval, sest korraldus antakse kell 16:00 või hiljem.

Näide: laupäeva hommikul antud korraldus – tehing tehakse ülejärgmisel tööpäeval, sest korraldus antakse laupäeval, pühapäeval või riigipühal.

## p 1.4.1–1.4.2 – Igaöine pangakontode ja registri rahakontode võrdlus

Tulemus on esimene, mille tingimus kehtib:

- viga: saldod ei klapi, kui panga lõppsaldo erineb registri rahakonto saldost päeva lõpus;
- viga: võrdlus on blokeeritud, kui registris on enne päeva lõppu lahendamata klassifitseerimata pangakandeid;
- kontroll on korras, kui panga lõppsaldo võrdub registri rahakonto saldoga päeva lõpus ja registris ei ole lahendamata klassifitseerimata pangakandeid.

Andmed: kontrollitakse kogumis-, väljamakse- ja fondikontot; kontroll käivitub, kui eelmise päeva väljavõte on pangast tõmmatud; väljavõtteid tõmmatakse iga 30 minuti järel kella 04:00–23:30; vea korral saadetakse teade Slacki ja kontroll lõpeb veaga; klassifitseerimata kandeid kontrollitakse alles siis, kui saldod klapivad.

Näide: saldod erinevad – viga: saldod ei klapi, sest panga lõppsaldo erineb registri rahakonto saldost päeva lõpus.

Näide: saldod klapivad, üks kanne on klassifitseerimata – viga: võrdlus on blokeeritud, sest panga lõppsaldo võrdub registri rahakonto saldoga päeva lõpus ja registris on enne päeva lõppu lahendamata klassifitseerimata pangakandeid.

Näide: saldod klapivad, kõik kanded on klassifitseeritud – kontroll on korras, sest panga lõppsaldo võrdub registri rahakonto saldoga päeva lõpus ja registris ei ole lahendamata klassifitseerimata pangakandeid.
