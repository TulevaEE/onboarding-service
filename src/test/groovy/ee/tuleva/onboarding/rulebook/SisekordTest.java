package ee.tuleva.onboarding.rulebook;

import static ee.tuleva.onboarding.rulebook.RuleTest.REBALANCE;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class SisekordTest {

  @Test
  void aSisekordWritesItsVersionACoverageListAndEachRule() {
    var sisekord =
        new Sisekord(
            4,
            "Investeerimistegevuse ja riskijuhtimise sisekord",
            "1.4",
            LocalDate.of(2026, 9, 30),
            List.of(REBALANCE));

    assertThat(sisekord.markdown())
        .isEqualTo(
            """
            # Sisekord 4: Investeerimistegevuse ja riskijuhtimise sisekord – reeglid

            Reeglid järgivad sisekorra versiooni 1.4, mis kehtib alates 30.09.2026.

            Tekst on genereeritud reeglite koodist ja iga näide on automaattest. Faili käsitsi ei muudeta.

            ## Kaetus

            Kood rakendab järgmisi sisekorra punkte; ülejäänud punktid on sisekorra tekstis.

            - p 2.6 – Suur fond tasakaalustatakse

            ## p 2.6 – Suur fond tasakaalustatakse

            Rakendub, kui fondil on vähemalt 10 positsiooni ja fondi raha on vähemalt 50 000 eurot.

            Andmed: positsioonid on depoopanga aruandest; raha on arvelduskonto jääk.

            Näide: suur fond palju raha – rakendub, sest fondil on vähemalt 10 positsiooni ja fondi raha on vähemalt 50 000 eurot.

            Näide: väike fond palju raha – ei rakendu, sest fondil on alla 10 positsiooni.
            """);
  }

  @Test
  void aSisekordListsADecisionInItsCoverageBesideItsRules() {
    var sisekord =
        new Sisekord(
            13,
            "Rahapesu tõkestamise sisekord",
            "1.1",
            LocalDate.of(2026, 6, 1),
            List.of(REBALANCE, DecisionTest.ONBOARDING));

    assertThat(sisekord.markdown())
        .contains(
            "- p 2.6 – Suur fond tasakaalustatakse\n- p 9.3 – Liitumise staatus riskitaseme järgi",
            "## p 9.3 – Liitumise staatus riskitaseme järgi");
  }

  @Test
  void aSisekordIsCheckedInUnderItsNumber() {
    var sisekord =
        new Sisekord(
            4,
            "Investeerimistegevuse ja riskijuhtimise sisekord",
            "1.4",
            LocalDate.of(2026, 9, 30),
            List.of(REBALANCE));

    assertThat(sisekord.fileName()).isEqualTo("sisekord-04.md");
  }
}
