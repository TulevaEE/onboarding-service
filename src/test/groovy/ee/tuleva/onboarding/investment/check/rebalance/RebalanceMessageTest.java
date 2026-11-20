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
  private static final LocalDate AWAITING_EXECUTION_UNTIL = LocalDate.of(2026, 12, 3);
  private static final List<Deviation> OUT_OF_LINE =
      List.of(
          new Deviation("IE000I9HGDZ3", new BigDecimal("0.80"), ZERO),
          new Deviation("IE00BFG1TM61", ZERO, new BigDecimal("0.79")));

  @Test
  void anOpeningWindow_reportsTheSisekordDeadlineDaysLeftAndWhatIsOff() {
    var window = window(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 9, 30), OUT_OF_LINE, null);

    assertThat(RebalanceMessage.of(window))
        .isEqualTo(
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
        .isEqualTo(
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
        .isEqualTo(
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

    assertThat(RebalanceMessage.of(window).text())
        .startsWith(
            "⚠️ TUV100 mudelportfell alates 01.09.2026: tähtaeg 01.12.2026, jäänud 0 päeva");
  }

  @Test
  void pastTheDeadline_failsCitingSisekord4P261() {
    var window = window(DEADLINE.plusDays(1), LocalDate.of(2026, 11, 30), OUT_OF_LINE, null);

    assertThat(RebalanceMessage.of(window))
        .isEqualTo(
            new RebalanceMessage(
                """
                🛑 TUV100 mudelportfell alates 01.09.2026: tähtaeg oli 01.12.2026, fond pole \
                mudeliga vastavuses ja Rebalance-jooksu tehinguid pole
                Üle 5 pp (NAV 30.11.2026): IE000I9HGDZ3 -80,00 pp, IE00BFG1TM61 +79,00 pp
                Vii fond 5 pp piiresse (sisekord 4 p 2.6.1)""",
                ERROR));
  }

  @Test
  void inTheWarningPeriodWithRebalanceOrdersSent_saysTheyAwaitExecution() {
    var window =
        window(
            WARNING_FROM,
            LocalDate.of(2026, 11, 13),
            OUT_OF_LINE,
            null,
            LocalDate.of(2026, 11, 13));

    assertThat(RebalanceMessage.of(window))
        .isEqualTo(
            new RebalanceMessage(
                """
                ⚠️ TUV100 mudelportfell alates 01.09.2026: tähtaeg 01.12.2026, jäänud 15 päeva; \
                Rebalance-jooksu tehingud saadetud 13.11.2026, ootavad SEB täitmise kinnitust
                Üle 5 pp (NAV 13.11.2026): IE000I9HGDZ3 -80,00 pp, IE00BFG1TM61 +79,00 pp
                Kontrolli SEB-st tehingute täitmist""",
                WARNING));
  }

  @Test
  void pastTheDeadlineWithRebalanceOrdersSentByIt_warnsThatTheyAwaitExecution() {
    var window =
        window(DEADLINE.plusDays(1), LocalDate.of(2026, 11, 30), OUT_OF_LINE, null, DEADLINE);

    assertThat(RebalanceMessage.of(window))
        .isEqualTo(
            new RebalanceMessage(
                """
                ⚠️ TUV100 mudelportfell alates 01.09.2026: tähtaeg oli 01.12.2026, fond pole \
                mudeliga vastavuses; Rebalance-jooksu tehingud saadetud 01.12.2026, ootavad SEB \
                täitmise kinnitust
                Üle 5 pp (NAV 30.11.2026): IE000I9HGDZ3 -80,00 pp, IE00BFG1TM61 +79,00 pp
                Kontrolli SEB-st tehingute täitmist (sisekord 4 p 2.6.1)""",
                WARNING));
  }

  @Test
  void pastTheGraceWithRebalanceOrdersSentByTheDeadline_failsSayingSebHasNotConfirmedThem() {
    var window =
        window(
            AWAITING_EXECUTION_UNTIL.plusDays(1),
            LocalDate.of(2026, 11, 30),
            OUT_OF_LINE,
            null,
            DEADLINE);

    assertThat(RebalanceMessage.of(window))
        .isEqualTo(
            new RebalanceMessage(
                """
                🛑 TUV100 mudelportfell alates 01.09.2026: tähtaeg oli 01.12.2026, fond pole \
                mudeliga vastavuses ja Rebalance-jooksu tehingud saadetud 01.12.2026, SEB pole \
                täitmist kinnitanud
                Üle 5 pp (NAV 30.11.2026): IE000I9HGDZ3 -80,00 pp, IE00BFG1TM61 +79,00 pp
                Vii fond 5 pp piiresse (sisekord 4 p 2.6.1)""",
                ERROR));
  }

  @Test
  void pastTheDeadlineWithRebalanceOrdersSentOnlyAfterIt_failsSayingWhenTheyWereSent() {
    var window =
        window(
            DEADLINE.plusDays(2),
            LocalDate.of(2026, 11, 30),
            OUT_OF_LINE,
            null,
            DEADLINE.plusDays(1));

    assertThat(RebalanceMessage.of(window))
        .isEqualTo(
            new RebalanceMessage(
                """
                🛑 TUV100 mudelportfell alates 01.09.2026: tähtaeg oli 01.12.2026, fond pole \
                mudeliga vastavuses ja Rebalance-jooksu tehingud saadetud 02.12.2026, SEB pole \
                täitmist kinnitanud
                Üle 5 pp (NAV 30.11.2026): IE000I9HGDZ3 -80,00 pp, IE00BFG1TM61 +79,00 pp
                Vii fond 5 pp piiresse (sisekord 4 p 2.6.1)""",
                ERROR));
  }

  @Test
  void aClosedWindow_namesTheNavThatClosedItEvenWhenANewerNavIsOff() {
    var closing = RebalanceClosure.byAnInLineNav(LocalDate.of(2026, 10, 30));
    var window = window(LocalDate.of(2026, 11, 3), LocalDate.of(2026, 11, 2), OUT_OF_LINE, closing);

    assertThat(RebalanceMessage.of(window))
        .isEqualTo(
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
        .isEqualTo(
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
            null,
            DEADLINE,
            WARNING_FROM,
            null);

    assertThat(RebalanceMessage.of(window).text()).contains("kõik osakaalud 2,5 pp piires");
  }

  private static RebalanceWindow window(
      LocalDate checkDate,
      @Nullable LocalDate latestNavDate,
      List<Deviation> outsideTolerance,
      @Nullable RebalanceClosure closure) {
    return window(checkDate, latestNavDate, outsideTolerance, closure, null);
  }

  private static RebalanceWindow window(
      LocalDate checkDate,
      @Nullable LocalDate latestNavDate,
      List<Deviation> outsideTolerance,
      @Nullable RebalanceClosure closure,
      @Nullable LocalDate rebalanceOrdersSentOn) {
    return new RebalanceWindow(
        TUV100,
        EFFECTIVE,
        checkDate,
        new BigDecimal("0.05"),
        latestNavDate,
        outsideTolerance,
        closure,
        rebalanceOrdersSentOn,
        DEADLINE,
        WARNING_FROM,
        awaitedThroughWhenSentByTheDeadline(rebalanceOrdersSentOn));
  }

  private static @Nullable LocalDate awaitedThroughWhenSentByTheDeadline(
      @Nullable LocalDate rebalanceOrdersSentOn) {
    return rebalanceOrdersSentOn != null && !rebalanceOrdersSentOn.isAfter(DEADLINE)
        ? AWAITING_EXECUTION_UNTIL
        : null;
  }
}
