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
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class RebalanceMessageTest {

  private static final LocalDate EFFECTIVE = LocalDate.of(2026, 9, 1);
  private static final LocalDate DEADLINE = LocalDate.of(2026, 12, 1);
  private static final LocalDate WARNING_FROM = LocalDate.of(2026, 11, 16);
  private static final List<Deviation> OUT_OF_LINE =
      List.of(
          new Deviation("IE000I9HGDZ3", new BigDecimal("0.80"), ZERO),
          new Deviation("IE00BFG1TM61", ZERO, new BigDecimal("0.79")));

  @Test
  void anOpeningWindow_reportsTheSisekordDeadlineDaysLeftAndWhatIsOff() {
    var window = window(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 9, 30), OUT_OF_LINE, null);

    assertThat(RebalanceMessage.of(window))
        .contains(
            new RebalanceMessage(
                """
                ℹ️ TUV100 mudelportfell alates 01.09.2026: tähtaeg 01.12.2026 (sisekord 4 p \
                2.6.1), jäänud 61 päeva
                Üle 5 pp (NAV 30.09.2026): IE000I9HGDZ3 -80,00 pp, IE00BFG1TM61 +79,00 pp""",
                INFO));
  }

  @Test
  void beforeTheFirstNavSinceTheEffectiveDate_saysTheNavIsNotPublishedYet() {
    var window = window(EFFECTIVE, null, List.of(), null);

    assertThat(RebalanceMessage.of(window))
        .contains(
            new RebalanceMessage(
                """
                ℹ️ TUV100 mudelportfell alates 01.09.2026: tähtaeg 01.12.2026 (sisekord 4 p \
                2.6.1), jäänud 91 päeva
                NAV alates 01.09.2026 pole veel avaldatud""",
                INFO));
  }

  @Test
  void inTheWarningPeriod_warnsWithTheDeadlineDaysLeftAndTheTradeToMake() {
    var window = window(WARNING_FROM, LocalDate.of(2026, 11, 13), OUT_OF_LINE, null);

    assertThat(RebalanceMessage.of(window))
        .contains(
            new RebalanceMessage(
                """
                ⚠️ TUV100 mudelportfell alates 01.09.2026: tähtaeg 01.12.2026, jäänud 15 päeva; \
                Rebalance-jooksu tehinguid pole
                Üle 5 pp (NAV 13.11.2026): IE000I9HGDZ3 -80,00 pp, IE00BFG1TM61 +79,00 pp
                Tee tasakaalustamise tehingud või vii fond tähtajaks 5 pp piiresse""",
                WARNING));
  }

  @Test
  void onTheDeadlineItself_warnsWithNoDaysLeft() {
    var window = window(DEADLINE, LocalDate.of(2026, 11, 30), OUT_OF_LINE, null);

    assertThat(RebalanceMessage.of(window).orElseThrow().text())
        .startsWith(
            "⚠️ TUV100 mudelportfell alates 01.09.2026: tähtaeg 01.12.2026, jäänud 0 päeva");
  }

  @Test
  void pastTheDeadline_failsCitingSisekord4P261() {
    var window = window(DEADLINE.plusDays(1), LocalDate.of(2026, 11, 30), OUT_OF_LINE, null);

    assertThat(RebalanceMessage.of(window))
        .contains(
            new RebalanceMessage(
                """
                🛑 TUV100 mudelportfell alates 01.09.2026: tähtaeg oli 01.12.2026, fond pole \
                mudeliga vastavuses ja Rebalance-jooksu tehinguid pole
                Üle 5 pp (NAV 30.11.2026): IE000I9HGDZ3 -80,00 pp, IE00BFG1TM61 +79,00 pp
                Vii fond 5 pp piiresse (sisekord 4 p 2.6.1)""",
                ERROR));
  }

  @Test
  void aClosedWindow_namesTheNavThatClosedItEvenWhenANewerNavIsOff() {
    var closing = RebalanceClosure.byAnInLineNav(LocalDate.of(2026, 10, 30));
    var window = window(LocalDate.of(2026, 11, 3), LocalDate.of(2026, 11, 2), OUT_OF_LINE, closing);

    assertThat(RebalanceMessage.of(window))
        .contains(
            new RebalanceMessage(
                "✅ TUV100 mudelportfell alates 01.09.2026: fond on mudeliga vastavuses (kõik"
                    + " osakaalud 5 pp piires, NAV 30.10.2026), kontroll suletud",
                INFO));
  }

  @Test
  void aWindowClosedByRebalancingTrades_saysWhenTheTradesWereMade() {
    var window =
        window(
            LocalDate.of(2026, 10, 29),
            LocalDate.of(2026, 10, 28),
            OUT_OF_LINE,
            RebalanceClosure.byRebalancingTrades(LocalDate.of(2026, 10, 28)));

    assertThat(RebalanceMessage.of(window))
        .contains(
            new RebalanceMessage(
                "✅ TUV100 mudelportfell alates 01.09.2026: tasakaalustamise tehingud tehtud"
                    + " 28.10.2026, kontroll suletud",
                INFO));
  }

  @Test
  void aSupersededWindowClosedByRebalancingTrades_isClosedAsRebalanced() {
    var window =
        window(
            LocalDate.of(2026, 11, 3),
            LocalDate.of(2026, 11, 2),
            OUT_OF_LINE,
            RebalanceClosure.byRebalancingTrades(LocalDate.of(2026, 10, 28)));

    assertThat(
            RebalanceMessage.ofSuperseded(new SupersededWindow(window, LocalDate.of(2026, 11, 3))))
        .isEqualTo(
            new RebalanceMessage(
                "✅ TUV100 mudelportfell alates 01.09.2026: tasakaalustamise tehingud tehtud"
                    + " 28.10.2026, kontroll suletud",
                INFO));
  }

  @Test
  void aSupersededWindowThatNeverCameIntoLine_isClosedWithTheModelThatReplacedIt() {
    var window = window(LocalDate.of(2026, 11, 3), LocalDate.of(2026, 11, 2), OUT_OF_LINE, null);

    assertThat(
            RebalanceMessage.ofSuperseded(new SupersededWindow(window, LocalDate.of(2026, 11, 3))))
        .isEqualTo(
            new RebalanceMessage(
                "⏹ TUV100 mudelportfell alates 01.09.2026: asendatud mudeliga alates 03.11.2026,"
                    + " vastavusse ei jõudnud; kontroll suletud",
                INFO));
  }

  @Test
  void aSupersededWindowThatCameIntoLine_isClosedAsInLine() {
    var closing = RebalanceClosure.byAnInLineNav(LocalDate.of(2026, 10, 30));
    var window = window(LocalDate.of(2026, 11, 3), LocalDate.of(2026, 11, 2), OUT_OF_LINE, closing);

    assertThat(
            RebalanceMessage.ofSuperseded(new SupersededWindow(window, LocalDate.of(2026, 11, 3))))
        .isEqualTo(
            new RebalanceMessage(
                "✅ TUV100 mudelportfell alates 01.09.2026: fond on mudeliga vastavuses (kõik"
                    + " osakaalud 5 pp piires, NAV 30.10.2026), kontroll suletud",
                INFO));
  }

  @Test
  void aToleranceWithDecimals_isWrittenWithADecimalComma() {
    var window =
        new RebalanceWindow(
            TUV100,
            EFFECTIVE,
            LocalDate.of(2026, 10, 30),
            new BigDecimal("0.025"),
            LocalDate.of(2026, 10, 30),
            List.of(),
            RebalanceClosure.byAnInLineNav(LocalDate.of(2026, 10, 30)),
            DEADLINE,
            WARNING_FROM);

    assertThat(RebalanceMessage.of(window).orElseThrow().text())
        .contains("kõik osakaalud 2,5 pp piires");
  }

  private static RebalanceWindow window(
      LocalDate checkDate,
      @Nullable LocalDate latestNavDate,
      List<Deviation> outsideTolerance,
      @Nullable RebalanceClosure closure) {
    return new RebalanceWindow(
        TUV100,
        EFFECTIVE,
        checkDate,
        new BigDecimal("0.05"),
        latestNavDate,
        outsideTolerance,
        closure,
        DEADLINE,
        WARNING_FROM);
  }
}
