# Sisekord 13: Rahapesu ja terrorismi rahastamise tõkestamise ning sanktsioonide rakendamise sisekord – reeglid

Reeglid järgivad sisekorra versiooni 1.1, mis kehtib alates 01.06.2026.

Tekst on genereeritud reeglite koodist ja iga näide on automaattest. Faili käsitsi ei muudeta.

## Kaetus

Kood rakendab järgmisi sisekorra punkte; ülejäänud punktid on sisekorra tekstis.

- p 4.1.7 – Vabatahtliku pensioni avaldus võetakse vastu pärast isiku tuvastamist
- p 9.3.3 – Täiendava Kogumisfondiga liitumine KYC riskitaseme järgi
- p 10.8 – Täiendava Kogumisfondi osakute tagasivõtmine hoolsusmeetmete kontrolli järgi

## p 4.1.7 – Vabatahtliku pensioni avaldus võetakse vastu pärast isiku tuvastamist

Rakendub, kui (isik on andnud PEP-deklaratsiooni, automaatne PEP-kontroll on läbitud või PEP-kontrolli tulemus on käsitsi üle kinnitatud), (sanktsioonikontroll on läbitud või sanktsioonikontrolli tulemus on käsitsi üle kinnitatud), (nimi vastab SK andmetele või nimi vastab Pensionikeskuse andmetele), isikut tõendav dokument on kontrollitud, tegevusala kontroll on läbitud ja (elukoha automaatne kontroll on läbitud või elukoht on käsitsi kontrollitud).

Andmed: arvesse lähevad viimase 365 päeva kontrollid; kontroll on läbitud, kui sellel perioodil on vähemalt üks edukas tulemus; PEP-deklaratsioon loetakse antuks olenemata sellest, mida isik deklareeris.

Näide: kõik automaatsed kontrollid on läbitud – rakendub, sest automaatne PEP-kontroll on läbitud, sanktsioonikontroll on läbitud, nimi vastab SK andmetele, isikut tõendav dokument on kontrollitud, tegevusala kontroll on läbitud ja elukoha automaatne kontroll on läbitud.

Näide: nimi vastab ainult Pensionikeskuse andmetele ja tulemused on käsitsi üle kinnitatud – rakendub, sest PEP-kontrolli tulemus on käsitsi üle kinnitatud, sanktsioonikontrolli tulemus on käsitsi üle kinnitatud, nimi vastab Pensionikeskuse andmetele, isikut tõendav dokument on kontrollitud, tegevusala kontroll on läbitud ja elukoht on käsitsi kontrollitud.

Näide: PEP-deklaratsiooni kontroll ei õnnestunud – rakendub, sest isik on andnud PEP-deklaratsiooni, sanktsioonikontroll on läbitud, nimi vastab SK andmetele, isikut tõendav dokument on kontrollitud, tegevusala kontroll on läbitud ja elukoha automaatne kontroll on läbitud.

Näide: sanktsioonikontroll puudub – ei rakendu, sest sanktsioonikontroll ei ole läbitud ja sanktsioonikontrolli tulemust ei ole käsitsi üle kinnitatud.

Näide: nime ei ole kummagi registriga võrreldud – ei rakendu, sest nime vastavust SK andmetele ei ole kinnitatud ja nime vastavust Pensionikeskuse andmetele ei ole kinnitatud.

Näide: tegevusala kontroll puudub – ei rakendu, sest tegevusala kontroll ei ole läbitud.

Näide: isikut tõendavat dokumenti ei ole kontrollitud – ei rakendu, sest isikut tõendavat dokumenti ei ole kontrollitud.

Näide: kontrolle ei ole – ei rakendu, sest isik ei ole andnud PEP-deklaratsiooni, automaatne PEP-kontroll ei ole läbitud, PEP-kontrolli tulemust ei ole käsitsi üle kinnitatud, sanktsioonikontroll ei ole läbitud, sanktsioonikontrolli tulemust ei ole käsitsi üle kinnitatud, nime vastavust SK andmetele ei ole kinnitatud, nime vastavust Pensionikeskuse andmetele ei ole kinnitatud, isikut tõendavat dokumenti ei ole kontrollitud, tegevusala kontroll ei ole läbitud, elukoha automaatne kontroll ei ole läbitud ja elukohta ei ole käsitsi kontrollitud.

## p 9.3.3 – Täiendava Kogumisfondiga liitumine KYC riskitaseme järgi

Tulemus on esimene, mille tingimus kehtib:

- liitumine jääb lõpetatuks, kui isiku liitumine on juba lõpetatud;
- liitumine lõpetatakse, kui riskitase puudub või riskitase on madal;
- liitumine jääb ootele, kui riskitase on keskmine;
- liitumine lükatakse tagasi, kui riskitase on kõrge.

Andmed: riskitase on isiku viimase KYC kontrolli tulemus.

Näide: uus madala riskiga isik – liitumine lõpetatakse, sest isiku liitumine ei ole lõpetatud ja riskitase on madal.

Näide: ootel isik, kelle riskitase puudub – liitumine lõpetatakse, sest isiku liitumine ei ole lõpetatud ja riskitase puudub.

Näide: uus keskmise riskiga isik – liitumine jääb ootele, sest isiku liitumine ei ole lõpetatud, riskitase on määratud, riskitase ei ole madal ja riskitase on keskmine.

Näide: ootel kõrge riskiga isik – liitumine lükatakse tagasi, sest isiku liitumine ei ole lõpetatud, riskitase on määratud, riskitase ei ole madal, riskitase ei ole keskmine ja riskitase on kõrge.

Näide: lõpetatud liitumisega isik, kelle riskitase tõuseb kõrgeks – liitumine jääb lõpetatuks, sest isiku liitumine on juba lõpetatud.

Näide: tagasi lükatud isik, kelle riskitase langeb madalaks – liitumine lõpetatakse, sest isiku liitumine ei ole lõpetatud ja riskitase on madal.

## p 10.8 – Täiendava Kogumisfondi osakute tagasivõtmine hoolsusmeetmete kontrolli järgi

Tulemus on esimene, mille tingimus kehtib:

- kontrolli korratakse, tähtaja möödudes täidetakse tagasivõtmine, kuid väljamakse peatatakse, kui sanktsioonide ja PEP-i skriinimine ei vasta;
- tagasivõtmine külmutatakse (p 6.3.2), kui skriinimine leiab sanktsiooni;
- kontrolli korratakse, tähtaja möödudes täidetakse tagasivõtmine, kuid väljamakse peatatakse, kui riskitase ei ole kättesaadav, skriinimine ei leia PEP-i, ettevõtte skriinimine ei katke ja ettevõtte liitumine ei jää lõpetamata;
- tagasivõtmine täidetakse, kuid väljamakse peatatakse, kui skriinimine leiab, et isik on PEP, ettevõtte uus skriinimine katkeb veaga, ettevõtte liitumine jääb pärast uut skriinimist lõpetamata, riskitase on kõrge või riskitase ei ole kättesaadav;
- tagasivõtmine kinnitatakse, kui skriinimine ei leia PEP-i, ettevõtte skriinimine ei katke, ettevõtte liitumine ei jää lõpetamata, riskitase ei ole kõrge ja riskitase on kättesaadav.

Andmed: isikut skriinitakse tema elukoha- ja kodakondsusriikide järgi; ettevõtet skriinitakse äriregistri andmetel uuesti, kui tema liitumine ei ole lõpetatud; riskitase on aml-service'i hinnang; seda küsitakse, kui skriinimine vastas ega leidnud sanktsiooni.

Näide: isik, kelle skriinimine ei vasta – kontrolli korratakse, tähtaja möödudes täidetakse tagasivõtmine, kuid väljamakse peatatakse, sest sanktsioonide ja PEP-i skriinimine ei vasta.

Näide: sanktsioneeritud isik – tagasivõtmine külmutatakse (p 6.3.2), sest skriinimine vastab ja skriinimine leiab sanktsiooni.

Näide: isik, kelle riskitase ei ole kättesaadav – kontrolli korratakse, tähtaja möödudes täidetakse tagasivõtmine, kuid väljamakse peatatakse, sest skriinimine vastab, skriinimine ei leia sanktsiooni, riskitase ei ole kättesaadav, skriinimine ei leia PEP-i, ettevõtte skriinimine ei katke ja ettevõtte liitumine ei jää lõpetamata.

Näide: PEP, kelle riskitase ei ole kättesaadav – tagasivõtmine täidetakse, kuid väljamakse peatatakse, sest skriinimine vastab, skriinimine ei leia sanktsiooni, skriinimine leiab, et isik on PEP ja riskitase ei ole kättesaadav.

Näide: kõrge riskiga PEP – tagasivõtmine täidetakse, kuid väljamakse peatatakse, sest skriinimine vastab, skriinimine ei leia sanktsiooni, riskitase on kättesaadav, skriinimine leiab, et isik on PEP ja riskitase on kõrge.

Näide: liitumata ettevõte, mille uus skriinimine katkeb veaga – tagasivõtmine täidetakse, kuid väljamakse peatatakse, sest skriinimine vastab, skriinimine ei leia sanktsiooni, riskitase on kättesaadav ja ettevõtte uus skriinimine katkeb veaga.

Näide: ettevõte, mille liitumine jääb pärast uut skriinimist lõpetamata – tagasivõtmine täidetakse, kuid väljamakse peatatakse, sest skriinimine vastab, skriinimine ei leia sanktsiooni, riskitase on kättesaadav ja ettevõtte liitumine jääb pärast uut skriinimist lõpetamata.

Näide: liitunud ettevõte, mille riskitase ei ole kõrge – tagasivõtmine kinnitatakse, sest skriinimine vastab, skriinimine ei leia sanktsiooni, riskitase on kättesaadav, skriinimine ei leia PEP-i, ettevõtte skriinimine ei katke, ettevõtte liitumine ei jää lõpetamata ja riskitase ei ole kõrge.

Näide: isik, kelle skriinimine ei leia midagi ja riskitase ei ole kõrge – tagasivõtmine kinnitatakse, sest skriinimine vastab, skriinimine ei leia sanktsiooni, riskitase on kättesaadav, skriinimine ei leia PEP-i, ettevõtte skriinimine ei katke, ettevõtte liitumine ei jää lõpetamata ja riskitase ei ole kõrge.
