# Sisekord 4: Investeerimistegevuse ja riskijuhtimise sisekord – reeglid

Reeglid järgivad sisekorra versiooni 1.4, mis kehtib alates 30.09.2026.

Tekst on genereeritud reeglite koodist ja iga näide on automaattest. Faili käsitsi ei muudeta.

## Kaetus

Kood rakendab järgmisi sisekorra punkte; ülejäänud punktid on sisekorra tekstis.

- p 2.4.3.11 – Täiendava Kogumisfondi osalus ühes investeerimisfondis
- p 2.5.3 – Laekuv raha on vaba raha juba enne laekumist
- p 3.6.4.1 – Optimaalset likviidsusreservi ületav vaba raha
- p 3.6.5 – Investeerimisfondi osakaalu jälgimislävi ja limiit
- p 9.5 – Optimaalse ja minimaalse likviidsusreservi kontroll
- p 11.3.1–11.3.3 – Hilinenud puhasväärtusest teavitatakse kiireloomuliselt
- p 11.3.3 – Täiendava Kogumisfondi puhasväärtus avaldatakse kell 16:00
- p 11.11.3 – Puhasväärtuse aruanne peatatakse sisemiseks ülevaatuseks

## p 2.4.3.11 – Täiendava Kogumisfondi osalus ühes investeerimisfondis

Tulemus on esimene, mille tingimus kehtib:

- häireteade, kui osalus sihtfondis ulatub limiidini;
- teade, kui osalus sihtfondis ületab jälgimisläve;
- korras, kui osalus sihtfondis jääb alla limiidi ja osalus sihtfondis ei ületa jälgimisläve.

Andmed: osalus on Täiendava Kogumisfondi positsiooni väärtus sihtfondi mahust; jälgimislävi ja limiit on parameetrid investment_ownership_limit andmebaasist; kontroll tehakse kord kuus kuu lõpu seisuga.

Näide: osalus täpselt 25% – häireteade, sest osalus sihtfondis ulatub limiidini.

Näide: osalus 22% – teade, sest osalus sihtfondis jääb alla limiidi ja osalus sihtfondis ületab jälgimisläve.

Näide: osalus 10% – korras, sest osalus sihtfondis jääb alla limiidi ja osalus sihtfondis ei ületa jälgimisläve.

## p 2.5.3 – Laekuv raha on vaba raha juba enne laekumist

Rakendub, kui arvestuspäev on 01.01.2027 või hiljem ja arvutus tehakse 01.01.2027 või hiljem.

Andmed: arvestuspäev on päev, mille kohta Fondi vaba raha arvutatakse; laekuv raha on SEB positsiooniaruande nõuete kontol olev Fondi osakute nõue; arvutuse tegemise päev on Tallinna aja järgi.

Näide: arvutus tehakse 01.01.2027 sama päeva kohta – rakendub, sest arvestuspäev on 01.01.2027 või hiljem ja arvutus tehakse 01.01.2027 või hiljem.

Näide: arvutus tehakse 31.12.2026 sama päeva kohta – ei rakendu, sest arvestuspäev on enne 01.01.2027 ja arvutus tehakse enne 01.01.2027.

Näide: arvutus tehakse 31.12.2026 päeva 01.01.2027 kohta – ei rakendu, sest arvutus tehakse enne 01.01.2027.

Näide: arvutus tehakse 01.01.2027 päeva 31.12.2026 kohta – ei rakendu, sest arvestuspäev on enne 01.01.2027.

## p 3.6.4.1 – Optimaalset likviidsusreservi ületav vaba raha

Rakendub, kui vaba raha ületab lubatud ülempiiri.

Andmed: vaba raha on raha arvelduskontol, millele liidetakse kohustused (negatiivsed), millest lahutatakse ootel tehingute rahamõju ja optimaalne likviidsusreserv; ülempiir on parameeter investment_fund_limit.max_free_cash andmebaasist; ülempiiri ületamine salvestatakse, kuid Slacki teadet ei saadeta.

Näide: vaba raha 120 000, ülempiir 100 000 – rakendub, sest vaba raha ületab lubatud ülempiiri.

Näide: vaba raha täpselt ülempiiril – ei rakendu, sest vaba raha ei ületa lubatud ülempiiri.

## p 3.6.5 – Investeerimisfondi osakaalu jälgimislävi ja limiit

Tulemus on esimene, mille tingimus kehtib:

- häireteade (limiit ületatud), kui fondi osakaal ületab limiiti;
- teade (jälgimislävi ületatud), kui fondi osakaal ületab jälgimisläve;
- korras, kui fondi osakaal ei ületa limiiti ja fondi osakaal ei ületa jälgimisläve.

Andmed: osakaal on positsiooni turuväärtus fondi puhasväärtusest; jälgimislävi ja limiit on parameetrid investment_position_limit.soft_limit_percent ja hard_limit_percent andmebaasist, iga ISIN-i kohta.

Näide: osakaal 20,5%, lävi 19,65%, limiit 20% – häireteade (limiit ületatud), sest fondi osakaal ületab limiiti.

Näide: osakaal täpselt limiidil 20% – teade (jälgimislävi ületatud), sest fondi osakaal ei ületa limiiti ja fondi osakaal ületab jälgimisläve.

Näide: osakaal 15% – korras, sest fondi osakaal ei ületa limiiti ja fondi osakaal ei ületa jälgimisläve.

## p 9.5 – Optimaalse ja minimaalse likviidsusreservi kontroll

Tulemus on esimene, mille tingimus kehtib:

- häireteade, kui raha arvelduskontol on alla minimaalse likviidsusreservi;
- teade, kui raha arvelduskontol on alla optimaalse likviidsusreservi;
- korras, kui raha arvelduskontol ei ole alla minimaalse likviidsusreservi ja raha arvelduskontol ei ole alla optimaalse likviidsusreservi.

Andmed: optimaalne ja minimaalne reserv on parameetrid investment_fund_limit.reserve_soft ja reserve_hard andmebaasist; kui fondil reservi parameetreid ei ole, kontrolli ei tehta.

Näide: raha 40 000, reserv 100 000 / 50 000 – häireteade, sest raha arvelduskontol on alla minimaalse likviidsusreservi.

Näide: raha 80 000, reserv 100 000 / 50 000 – teade, sest raha arvelduskontol ei ole alla minimaalse likviidsusreservi ja raha arvelduskontol on alla optimaalse likviidsusreservi.

Näide: raha täpselt optimaalse reservi suurune – korras, sest raha arvelduskontol ei ole alla minimaalse likviidsusreservi ja raha arvelduskontol ei ole alla optimaalse likviidsusreservi.

## p 11.3.1–11.3.3 – Hilinenud puhasväärtusest teavitatakse kiireloomuliselt

Rakendub, kui täna on tööpäev ja fondi puhasväärtuse aruanne on avaldamata.

Andmed: tähtaeg on TUK75 ja TUK00 puhul kell 11:30, TUV100 ja TKF100 puhul kell 15:30; kontroll tehakse 5 minutit pärast tähtaega; eelhoiatus saadetakse veidi pärast arvutuse algust samal tingimusel; oodatav aruanne on eelmise tööpäeva positsioonide põhjal arvutatud puhasväärtus.

Näide: tööpäeval tähtajaks avaldamata aruanne – rakendub, sest täna on tööpäev ja fondi puhasväärtuse aruanne on avaldamata.

Näide: tööpäeval avaldatud aruanne – ei rakendu, sest fondi puhasväärtuse aruande puudumist ei ole tuvastatud.

Näide: laupäev – ei rakendu, sest täna on laupäev, pühapäev või riigipüha ja fondi puhasväärtuse aruande puudumist ei ole tuvastatud.

## p 11.3.3 – Täiendava Kogumisfondi puhasväärtus avaldatakse kell 16:00

Tulemus on esimene, mille tingimus kehtib:

- näidatakse täna avaldatud puhasväärtust, kui täna on tööpäev ja kell on 16:00 või hiljem;
- näidatakse eelmisel tööpäeval avaldatud puhasväärtust, kui täna on laupäev, pühapäev või riigipüha või kell on enne 16:00.

Andmed: aeg on Eesti aeg; tööpäeval arvutatakse eelmise tööpäeva puhasväärtus; avaldamise aeg on ühtlasi märkimis- ja tagasivõtmiskorralduste cut-off.

Näide: tööpäeval kell 16:00 – näidatakse täna avaldatud puhasväärtust, sest täna on tööpäev ja kell on 16:00 või hiljem.

Näide: tööpäeval kell 15:59 – näidatakse eelmisel tööpäeval avaldatud puhasväärtust, sest kell on enne 16:00.

Näide: laupäeval kell 17:00 – näidatakse eelmisel tööpäeval avaldatud puhasväärtust, sest täna on laupäev, pühapäev või riigipüha.

## p 11.11.3 – Puhasväärtuse aruanne peatatakse sisemiseks ülevaatuseks

Rakendub, kui jälgimisvahe kontroll lõpeb ja mudelportfelli NAV-jääk ulatub lävendini.

Andmed: pooleli jäänud kontrollist teavitatakse Slackis, aruanne edastatakse; NAV-jääk on fondi tootluse erinevus päeva alguse positsioonide põhjal oodatud tootlusest; lävend on parameeter TRACKING_BREACH_THRESHOLD andmebaasist; kui päeva alguse positsioonid puuduvad, NAV-jääki ei hinnata ja see loetakse lävendist väiksemaks; jälgimisvahe lävendi ületamine aruannet ei peata, seda käsitletakse p 11.8 korras.

Näide: NAV-jääk ulatub lävendini – rakendub, sest jälgimisvahe kontroll lõpeb ja mudelportfelli NAV-jääk ulatub lävendini.

Näide: NAV-jääk jääb alla lävendi – ei rakendu, sest mudelportfelli NAV-jääk jääb alla lävendi.

Näide: hinnad puuduvad, kuigi osaline tulemus ulatub lävendini – ei rakendu, sest jälgimisvahe kontroll jääb puuduvate hindade või vea tõttu pooleli.

Näide: kontroll katkeb veaga – ei rakendu, sest jälgimisvahe kontroll jääb puuduvate hindade või vea tõttu pooleli ja mudelportfelli NAV-jääk jääb alla lävendi.
