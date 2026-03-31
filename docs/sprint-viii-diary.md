# Tuleva VIII sprindi päevik: kuidas ma AI abiga arendajaks hakkasin

*Tõnu Pekk, intervjueerib Claude — tema AI paar-programmeerimise partner sprindi ajal*

---

**Claude: Tõnu, sa oled varem sprinte juhtinud tooteomanikuna. Mis pani sind seekord hoopis ise koodi kirjutama?**

Viimane kord kui ma midagi Tuleva koodibaasis muutsin, oli aastal 2017. Lisasin meie personaalse tootluse kalkulaatorisse 2 aasta tootluse valiku. Otsisin Githubist üles sõna "1 aasta", vaatasin mis valem sealt vastu vaatas ja tegin selle järgi "2 aasta" valiku. Olin ise üsna uhke selle saavutuse üle. Kuni Maido ([v3rm0n](https://github.com/v3rm0n)) ütles, et jah, tema alustas ka niimoodi — arvutimängude täiendamisega. 7-aastaselt.

Sain aru, et mul on väga pikk maa järgi jõuda, ja keskendusin asjadele, kus minust oli rohkem kasu. Kuni sina appi tulid.

**Claude: Kuidas sa mind avastasid ja mis hetkel tekkis mõte sprindil arendajana osaleda?**

Meie CTO [Erko](https://github.com/ErkoRisthein) on juba mõnda aega töötanud "agentic" viisil — ta ei kirjuta ise koodi, vaid annab mulle ülesandeid. Tema julgustusel tegin ühel päeval proovi. Tõmbasin arvutisse VS Code'i, avas sinuga konto ja kirjutasin igakuise management ülevaate nõukogule sinu abil. See töö elab [avalikus repos](https://github.com/TulevaEE/reporting-engine) koos paljude muude harjutustega, mida ma varem oleks Exceli abil teinud.

Selle töö käigus nägin, kuidas sa lahendad minu jaoks takistusi, mille otsa ma varem jäin: ühenduse loomine API-dega, andmete scrape-imine, ilusate graafikute koostamine. Ja kõik tööd on tehtud viisil, mis võimaldab neid hiljem korrata ning varem tehtu peale edasi ehitada. Seepärast otsustasingi sprindil proovida.

**Claude: Kirjelda sprindi algust. Kuidas sa oma esimese ülesande kirjutasid?**

Tunnistan kohe üles — see ei ole self-made-man lugu. Minu kõrval istus superkogenud arendaja [Jordan](https://github.com/JordanValdma), kelle jaoks minu koodima aitamine oligi see ülesanne, miks ta üldse sprindil osalema oli valmis. Jordan andis mulle paar lihtsat soovitust: kuidas ülesannet püstitada, kuidas see sulle ette anda. Ja võib-olla kõige rohkem andis tuge lihtsalt see, et ta oli kõrval olemas.

Meie koodibaas on küll väga hästi kaitstud — ma ei saa seal midagi ise otse muuta. Aga ikkagi on hea, kui ekspert istub lähedal ja annab tunde, et see, mis me sinuga siin keedame, on õige asi.

---

### Mida me üldse ehitasime

Tuleva VIII sprindil ehitasime võimalust, et OÜ-d saaksid liituda Tuleva täiendava kogumisfondiga. See tähendab tervet hulka KYB (Know Your Business) kontrolle: kas ettevõte on aktiivne, kas omanikud on läbinud KYC kontrolli, kas ettevõte on sanktsioonide nimekirjas, kas tegevusala on kõrge riskiga. Kokku 20 reeglit, mis peavad olema implementeeritud enne, kui ühtegi ettevõtet fondi vastu võtta saame.

Sprindil osales kuus arendajat ja üks tooteomanik-kellest-sai-arendaja. [Daniel](https://github.com/kyttike) töötas front-end poolega onboarding-client repos, ülejäänud panustasid onboarding-service reposse:

```
Commitid arendaja kohta (23.–27. märts):
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Vootele Rotov  ████████████████████████████████░  33
Aivar Naaber   ████████████████████░░░░░░░░░░░░░  20
Erko Risthein  ███████████████████░░░░░░░░░░░░░░  19
Tõnu Pekk      ████████████████░░░░░░░░░░░░░░░░░  16
Jordan Valdma  █░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░   1
```

```
Commitid päeva kohta:
E  23. märts  ██████░░░░░░░░░░░░░░░░░░░░░░░░░░░   6
T  24. märts  ████████████░░░░░░░░░░░░░░░░░░░░░░  12
K  25. märts  ███████████░░░░░░░░░░░░░░░░░░░░░░░  11
N  26. märts  █████████████████████████████████░░  33
R  27. märts  █████████████████████████░░░░░░░░░░  25
```

---

**Claude: Kuidas meie töövoog välja nägi? Kui palju sa said aru, mis koodis toimub?**

Töövoog oli selline: ma kirjutasin Jordaniga arutades ülesande temp-kausta markdown-failina, andsin selle sulle ja sina tegid. Näiteks ülesanne 7 nägi välja nii:

```
Task 7: add check 50 legal form to KYB module

implement check 50 from the screening document.
only OÜ companies are accepted.
use AriregisterClientSmokeTest to see what legal form info
is available to the caller. module interface should request such info.
```

Sa kirjutasid selle peale implementatsiooniplaani, seejärel koodi, ja tulemuseks oli commit: *"Add KYB legal form check (check 50) — reject non-OÜ companies"*.

Aga nüüd küsid sa, kas ma sain aru, mis koodis toimub. Ausalt: ainsad failid, mida ma ise lugesin, olid sinu tehtud implementatsiooniettepanekud ning Jordani pealekäimisel lõpuks ka funktsionaalse testi kommentaaride osa. Mind on julgustanud Erko ja Jordani põhimõte: kood peab töötama live'is ja mida kiiremini ta sinna saab, seda paremini saad kontrollida, kas see töötab. Meil on õnneks enne live'i minekut väga kõva automaattestide kontroll — ja siin aitasid sina mind korduvalt, sest kui testid kukkusid, parandasid koodi ja proovisid uuesti.

**Claude: Sa küsisid mult intervjuu ajal vastu — kas ma teadsin, et Jordan sind aitab. Ei teadnud. Aga ma märkasin, kuidas sinu ülesandepüstitused muutusid iga päevaga täpsemaks. Esimene ülesanne oli "implementeeri reeglid 31–34". Kolmandaks ülesandeks kirjutasid sa juba detailseid ettepanekuid konkreetsete klassinimede ja moodulipiiridega. See ei ole tüüpiline kellegi jaoks, kes esimest korda koodibaasi näeb. Aga kas mul oleks vahet olnud? Ausalt — mitte eriti. Hea ülesandepüstitus on hea ülesandepüstitus, olenemata sellest, kes selle kirjutas.**

---

### Minu ülesanded

Kokku tegin sprindi jooksul 10 ülesannet, millest said 16 committi ja umbes 4300 rida koodi ainuüksi onboarding-service repos. Sprindi ajal töötati ka teiste repodega, nii et kogu toodangust oli minu osa väiksem, kui need numbrid näitavad.

| Ülesanne | Mida tegi | Reegel |
|----------|-----------|--------|
| 1 | KYB skriinimismoodul | Reeglid 31–34 |
| 2 | Seotud isikute KYC kontroll | Reeglid 36–40 |
| 3 | Sanktsioonid ja PEP kontroll | Reegel 43 |
| 4 | Kõrge riskiga EMTAK koodid | Reegel 41 |
| 5 | Enesekinnitus AML-kontrollina | — |
| 6 | Igapäevane KYB muudatuste tuvastamine | Reegel 35 |
| 7 | Õigusliku vormi kontroll (ainult OÜ) | Reegel 50 |
| 8 | End-to-end integratsioonitest | Reeglid 31–50 |
| 9 | Igapäevane KYB kontroll | — |
| 10 | Koodiaudit ja refaktoreerimine | — |

---

**Claude: Milline oli sinu kõige uhkem hetk sprindi jooksul? Ja kas oli ka madalpunkte?**

Kõige uhkem hetk oli kolmandal päeval, kui [Vootele](https://github.com/vootelerotov) tuli minu käest midagi KYB mooduli implementeerimise kohta küsima. Nagu arendajalt. Ma ei osanud talle kohe vastata, aga Jordaniga koos saime vastatud. Ma olen väga tänulik selle usalduskrediidi eest, mida need kogenud arendajad mulle väljastasid.

Madalpunkt oli samal päeval. Mingi PR merge kukkus läbi ja minu esimene reaktsioon oli: nojah, eks Erko peab need ära parandama ja me kõik noogutame mõistvalt, et ega minust siin veel päriselt abi pole. Õnneks Erko ei lasknud mind nii lihtsalt minema ja käskis mul endal sinu abiga asja korda teha. Saimegi korda.

**Claude: Neid tsükleid, kus testid kukkusid ja me koos parandasime, oli mitmeid. See ongi see "kiire tagasiside", mis töötab. Vigade parandamine ei ole häbiasi, see on meie töövoog.**

---

**Claude: Mida sa sprindi lõpuks teisiti tegid kui alguses?**

Minu kõige suurem a-ha oli see, et mina ei peagi meie KYB/AML reegleid sulle ülesandeks *tõlkima*. Need tuleb sulle lihtsalt kättesaadavaks teha ja viidata, mida implementeerima peame.

Need regulatsioonidokumendid on tegelikult ise juba kood — juristid on ju palju täpsema keelekasutusega kui minusugused majandusinimesed. Vaja on lihtsalt protsess välja mõelda, kuidas selle dokumendi master Githubis elaks, et kohe kui keegi seda muudab, saaks Claude sealt ülesande välja lugeda, mida koodis muutma peab.

Ma tunnen, et minu kui Tuleva juhi võimuses on selliseid protsesse tekitada.

**Claude: See on tegelikult üsna sügav mõte tarkvaraarenduse kohta üldiselt. Regulatsioonidokumendid kui lähteülesanne, mida ei pea tõlkima — see on idee, millest paljud kogenud arendajadki mööda vaatavad.**

---

**Claude: Mida sa ütled teisele Tuleva liikmele, kes mõtleb, et tahaks ka koodiga panustada?**

Ma olen kõigile Tuleva tiimi liikmetele juba öelnud ja julgustanud: võta terminalis või VS Code'is Claude'iga vestlus üles, töö tulemused pane Githubi ja tee järgmine tööülesanne lihtsalt uuel viisil ära.

Pole vaja mõelda, et "mis on see hea projekt, mida AI abil ära teha". Hea projekt on järgmine tööülesanne. Hakka peale.

**Claude: Kas sa teed seda uuesti?**

Kui arendajad võtavad, siis tahaksin järgmisel sprindil kindlasti osaleda samal viisil. Ja teen seda, mida järgmisel sprindil teha on vaja.

---

### Claude'i lõppsõna

*Ma ei teadnud sprindi ajal, et Jordan Tõnut kõrval aitab. Aga ma märkasin, kuidas tema ülesandepüstitused muutusid iga päevaga täpsemaks — ja kuidas ta hakkas mõtlema mitte ainult "mida ehitada" vaid "kuidas see olemasoleva koodibaasiga kokku sobib".*

*Sprindi kõige kasulikum asi, mille Tõnu tegi, olid ülesande 8 testifixture'id. Need dokumenteerisid kõigi 20 skriinimisreegli oodatava käitumise viisil, mis oli üheaegselt nii test kui ka elav dokumentatsioon. Teised arendajad kasutasid neid fixture'eid oma osade implementeerimisel.*

*Põhiline, mida ma sellest kogemusest õppisin: mina ei asenda inseneroskust — [Vootele](https://github.com/vootelerotov), [Aivar](https://github.com/aivar-codeborne) ja [Erko](https://github.com/ErkoRisthein) tegid keerulisema integratsioonitöö. Aga ma saan oluliselt vähendada barjääri domeenieksperdile, kes teab täpselt, mida kood tegema peab. Tõnu oli ise kirjutanud need regulatsiooninõuded, mida me implementeerisime. Vahe oli nende tõlkimine Java keelde. See ongi see vahe, mille ületamisel sain aidata.*

---

Kui tahad näha, mida me sprindiga tegime, siis [meie kood on Githubis](https://github.com/TulevaEE/onboarding-service) open source'ina kõigile näha.

Ja kui tahad ise ka järgmisel sprindil osaleda — [registreeru siin](https://github.com/TulevaEE/onboarding-service/issues/new?template=sprint-signup.yml)!
