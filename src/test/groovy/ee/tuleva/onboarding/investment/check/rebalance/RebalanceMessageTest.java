package ee.tuleva.onboarding.investment.check.rebalance;

import static ee.tuleva.onboarding.notification.OperationsNotificationService.Severity.ERROR;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Severity.INFO;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Severity.WARNING;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUV100;
import static java.math.BigDecimal.ZERO;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class RebalanceMessageTest {

  private static final LocalDate EFFECTIVE = LocalDate.of(2026, 10, 1);
  private static final RebalanceDeadlines DEADLINES =
      new RebalanceDeadlines(
          LocalDate.of(2026, 11, 1), LocalDate.of(2026, 12, 1), LocalDate.of(2027, 1, 4));
  private static final List<Deviation> OUT_OF_LINE =
      List.of(
          new Deviation("IE000I9HGDZ3", new BigDecimal("0.80"), ZERO),
          new Deviation("IE00BFG1TM61", ZERO, new BigDecimal("0.79")));

  @Test
  void inTheFirstMonth_reportsDaysSinceTheEffectiveDateDaysLeftAndWhatIsOff() {
    var window =
        window(LocalDate.of(2026, 10, 5), Set.of(), LocalDate.of(2026, 10, 2), OUT_OF_LINE, null);

    assertThat(RebalanceMessage.of(window))
        .contains(
            new RebalanceMessage(
                """
                ℹ️ TUV100 mudelportfell alates 01.10.2026: jõustumisest 4 päeva, sisemine tähtaeg \
                01.12.2026 (57 päeva), seaduslik 04.01.2027
                Üle 5 pp (NAV 02.10.2026): IE000I9HGDZ3 -80,00 pp, IE00BFG1TM61 +79,00 pp; \
                tehingud: pole""",
                INFO));
  }

  @Test
  void beforeTheFirstNavSinceTheEffectiveDate_saysTheNavIsNotPublishedYet() {
    var window = window(LocalDate.of(2026, 10, 1), Set.of("IE000I9HGDZ3"), null, List.of(), null);

    assertThat(RebalanceMessage.of(window))
        .contains(
            new RebalanceMessage(
                """
                ℹ️ TUV100 mudelportfell alates 01.10.2026: jõustumisest 0 päeva, sisemine tähtaeg \
                01.12.2026 (61 päeva), seaduslik 04.01.2027
                NAV alates 01.10.2026 pole veel avaldatud; tehingud: IE000I9HGDZ3""",
                INFO));
  }

  @Test
  void aMonthWithoutTrades_warnsWithTheTradeToMake() {
    var window =
        window(LocalDate.of(2026, 11, 2), Set.of(), LocalDate.of(2026, 10, 30), OUT_OF_LINE, null);

    assertThat(RebalanceMessage.of(window))
        .contains(
            new RebalanceMessage(
                """
                ⚠️ TUV100 mudelportfell alates 01.10.2026: tehingute tähtaeg oli 01.11.2026, \
                muutunud instrumentides tehinguid pole
                Üle 5 pp (NAV 30.10.2026): IE000I9HGDZ3 -80,00 pp, IE00BFG1TM61 +79,00 pp
                Tee tasakaalustamise tehingud; sisemine tähtaeg 01.12.2026, seaduslik 04.01.2027""",
                WARNING));
  }

  @Test
  void twoMonthsOutOfLine_failsWithTheLegalDeadline() {
    var window =
        window(
            LocalDate.of(2026, 12, 1),
            Set.of("IE00BFG1TM61", "IE000I9HGDZ3"),
            LocalDate.of(2026, 11, 30),
            OUT_OF_LINE,
            null);

    assertThat(RebalanceMessage.of(window))
        .contains(
            new RebalanceMessage(
                """
                🛑 TUV100 mudelportfell alates 01.10.2026: sisemine tähtaeg oli 01.12.2026, fond \
                pole mudeliga vastavuses
                Üle 5 pp (NAV 30.11.2026): IE000I9HGDZ3 -80,00 pp, IE00BFG1TM61 +79,00 pp; \
                tehingud: IE000I9HGDZ3, IE00BFG1TM61
                Vii fond 5 pp piiresse; seaduslik tähtaeg 04.01.2027 (sisekord 4 p 2.6.1)""",
                ERROR));
  }

  @Test
  void pastTheLegalDeadline_failsSayingItWasMissed() {
    var window =
        window(LocalDate.of(2027, 1, 5), Set.of(), LocalDate.of(2027, 1, 4), OUT_OF_LINE, null);

    assertThat(RebalanceMessage.of(window).orElseThrow().text())
        .endsWith("Vii fond 5 pp piiresse; seaduslik tähtaeg oli 04.01.2027 (sisekord 4 p 2.6.1)");
  }

  @Test
  void theNavThatClosesTheWindow_announcesTheCloseOnce() {
    var closing = LocalDate.of(2026, 10, 30);

    assertThat(
            RebalanceMessage.of(
                window(LocalDate.of(2026, 11, 2), Set.of(), closing, List.of(), closing)))
        .contains(
            new RebalanceMessage(
                "✅ TUV100 mudelportfell alates 01.10.2026: fond on mudeliga vastavuses (kõik"
                    + " osakaalud 5 pp piires, NAV 30.10.2026), kontroll suletud",
                INFO));
    assertThat(
            RebalanceMessage.of(
                window(
                    LocalDate.of(2026, 11, 3),
                    Set.of(),
                    LocalDate.of(2026, 11, 2),
                    OUT_OF_LINE,
                    closing)))
        .isEmpty();
  }

  @Test
  void aToleranceWithDecimals_isWrittenWithADecimalComma() {
    var window =
        new RebalanceWindow(
            TUV100,
            EFFECTIVE,
            LocalDate.of(2026, 10, 30),
            new BigDecimal("0.025"),
            Set.of("IE00BFG1TM61"),
            Set.of(),
            LocalDate.of(2026, 10, 30),
            List.of(),
            LocalDate.of(2026, 10, 30),
            DEADLINES);

    assertThat(RebalanceMessage.of(window).orElseThrow().text())
        .contains("kõik osakaalud 2,5 pp piires");
  }

  private static RebalanceWindow window(
      LocalDate checkDate,
      Set<String> tradedIsins,
      @Nullable LocalDate latestNavDate,
      List<Deviation> outsideTolerance,
      @Nullable LocalDate inLineSince) {
    return new RebalanceWindow(
        TUV100,
        EFFECTIVE,
        checkDate,
        new BigDecimal("0.05"),
        Set.of("IE00BFG1TM61", "IE000I9HGDZ3"),
        tradedIsins,
        latestNavDate,
        outsideTolerance,
        inLineSince,
        DEADLINES);
  }
}
