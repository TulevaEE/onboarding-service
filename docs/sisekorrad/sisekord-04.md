# Sisekord 4: Investeerimistegevuse ja riskijuhtimise sisekord – reeglid

Reeglid järgivad sisekorra versiooni 1.4, mis kehtib alates 30.09.2026.

Tekst on genereeritud reeglite koodist ja iga näide on automaattest. Faili käsitsi ei muudeta.

## Kaetus

Kood rakendab järgmisi sisekorra punkte; ülejäänud punktid on sisekorra tekstis.

- p 2.5.3 – Laekuv raha on vaba raha juba enne laekumist
- p 11.11.3 – Puhasväärtuse aruanne peatatakse sisemiseks ülevaatuseks

## p 2.5.3 – Laekuv raha on vaba raha juba enne laekumist

Rakendub, kui arvestuspäev on 01.01.2027 või hiljem ja arvutus tehakse 01.01.2027 või hiljem.

Andmed: arvestuspäev on päev, mille kohta Fondi vaba raha arvutatakse; laekuv raha on SEB positsiooniaruande nõuete kontol olev Fondi osakute nõue; arvutuse tegemise päev on Tallinna aja järgi.

Näide: arvutus tehakse 01.01.2027 sama päeva kohta – rakendub, sest arvestuspäev on 01.01.2027 või hiljem ja arvutus tehakse 01.01.2027 või hiljem.

Näide: arvutus tehakse 31.12.2026 sama päeva kohta – ei rakendu, sest arvestuspäev on enne 01.01.2027 ja arvutus tehakse enne 01.01.2027.

Näide: arvutus tehakse 31.12.2026 päeva 01.01.2027 kohta – ei rakendu, sest arvutus tehakse enne 01.01.2027.

Näide: arvutus tehakse 01.01.2027 päeva 31.12.2026 kohta – ei rakendu, sest arvestuspäev on enne 01.01.2027.

## p 11.11.3 – Puhasväärtuse aruanne peatatakse sisemiseks ülevaatuseks

Rakendub, kui jälgimisvahe kontroll lõpeb ja mudelportfelli NAV-jääk ulatub lävendini.

Andmed: pooleli jäänud kontrollist teavitatakse Slackis, aruanne edastatakse; NAV-jääk on fondi tootluse erinevus päeva alguse positsioonide põhjal oodatud tootlusest; lävend on parameeter TRACKING_BREACH_THRESHOLD andmebaasist; kui päeva alguse positsioonid puuduvad, NAV-jääki ei hinnata ja see loetakse lävendist väiksemaks; jälgimisvahe lävendi ületamine aruannet ei peata, seda käsitletakse p 11.8 korras.

Näide: NAV-jääk ulatub lävendini – rakendub, sest jälgimisvahe kontroll lõpeb ja mudelportfelli NAV-jääk ulatub lävendini.

Näide: NAV-jääk jääb alla lävendi – ei rakendu, sest mudelportfelli NAV-jääk jääb alla lävendi.

Näide: hinnad puuduvad, kuigi osaline tulemus ulatub lävendini – ei rakendu, sest jälgimisvahe kontroll jääb puuduvate hindade või vea tõttu pooleli.

Näide: kontroll katkeb veaga – ei rakendu, sest jälgimisvahe kontroll jääb puuduvate hindade või vea tõttu pooleli ja mudelportfelli NAV-jääk jääb alla lävendi.
