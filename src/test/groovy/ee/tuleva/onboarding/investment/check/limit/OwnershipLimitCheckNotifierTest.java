package ee.tuleva.onboarding.investment.check.limit;

import static ee.tuleva.onboarding.investment.check.limit.BreachSeverity.HARD;
import static ee.tuleva.onboarding.investment.check.limit.BreachSeverity.OK;
import static ee.tuleva.onboarding.investment.check.limit.BreachSeverity.SOFT;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.INVESTMENT;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Severity.ERROR;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Severity.INFO;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Severity.WARNING;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TKF100;
import static org.mockito.BDDMockito.then;

import ee.tuleva.onboarding.investment.check.limit.OwnershipCheckRun.NotChecked;
import ee.tuleva.onboarding.investment.check.limit.OwnershipCheckRun.Result;
import ee.tuleva.onboarding.notification.OperationsNotificationService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OwnershipLimitCheckNotifierTest {

  private static final YearMonth MONTH = YearMonth.of(2026, 9);
  private static final LocalDate CHECK_DATE = LocalDate.of(2026, 9, 30);

  @Mock private OperationsNotificationService notificationService;

  @InjectMocks private OwnershipLimitCheckNotifier notifier;

  @Test
  void aMonthWithEveryHoldingAtOrUnderTwentyPercent_postsOneOkLineNamingTheLargest() {
    notifier.notify(run(result(List.of(invescoEm("3.20", OK), canada("0.20")), List.of())));

    then(notificationService)
        .should()
        .sendMessage(
            """
            ✅ Ownership limit check OK: month=2026-09
              ✅ TKF100 2026-09-30: 2 of 2 holdings checked, largest 3.20% of Invesco EM (IE00BMDBMY19) in a 150.00M EUR fund — soft 20%, hard 25%""",
            INVESTMENT, INFO);
  }

  @Test
  void aHoldingOverTwentyPercent_firesAYellowWarningNamingTheHolding() {
    notifier.notify(run(result(List.of(invescoEm("21.30", SOFT), canada("0.20")), List.of())));

    then(notificationService)
        .should()
        .sendMessage(
            """
            ⚠️ OWNERSHIP SOFT LIMIT EXCEEDED: month=2026-09
              ⚠️ TKF100 2026-09-30: 2 of 2 holdings checked, largest 21.30% of Invesco EM (IE00BMDBMY19) in a 150.00M EUR fund — soft 20%, hard 25%
              ⚠️ [SOFT] TKF100 2026-09-30 Invesco EM (IE00BMDBMY19): 21.30% — holding 31.95M EUR of a 150.00M EUR fund (EODHD: 174.00M USD, updated 2026-10-03), soft 20%, hard 25%""",
            INVESTMENT, WARNING);
  }

  @Test
  void aHoldingOfTwentyFivePercentOrMore_firesARedAlert() {
    notifier.notify(run(result(List.of(invescoEm("25.00", HARD), canada("0.20")), List.of())));

    then(notificationService)
        .should()
        .sendMessage(
            """
            🛑 OWNERSHIP LIMIT BREACH: month=2026-09
              🛑 TKF100 2026-09-30: 2 of 2 holdings checked, largest 25.00% of Invesco EM (IE00BMDBMY19) in a 150.00M EUR fund — soft 20%, hard 25%
              🛑 [HARD] TKF100 2026-09-30 Invesco EM (IE00BMDBMY19): 25.00% — holding 31.95M EUR of a 150.00M EUR fund (EODHD: 174.00M USD, updated 2026-10-03), soft 20%, hard 25%""",
            INVESTMENT, ERROR);
  }

  @Test
  void
      aHoldingWithoutAFundSize_makesTheMonthIncompleteRatherThanOk_withoutTheYellowOfASoftBreach() {
    notifier.notify(
        run(
            result(
                List.of(invescoEm("3.20", OK)),
                List.of(developedWorld("EODHD has no total assets")))));

    then(notificationService)
        .should()
        .sendMessage(
            """
            ⏸ Ownership limit check INCOMPLETE: month=2026-09
              ✅ TKF100 2026-09-30: 1 of 2 holdings checked, largest 3.20% of Invesco EM (IE00BMDBMY19) in a 150.00M EUR fund — soft 20%, hard 25%
              ⏸ TKF100 2026-09-30 iShares Developed World (IE00BFG1TM61): not verified — EODHD has no total assets""",
            INVESTMENT, INFO);
  }

  @Test
  void aFundWhereNoHoldingCouldBeChecked_firesARedAlertInsteadOfNamingALargest() {
    notifier.notify(run(result(List.of(), List.of(developedWorld("EODHD answered HTTP 404")))));

    then(notificationService)
        .should()
        .sendMessage(
            """
            ⏸ Ownership limit check INCOMPLETE: month=2026-09
              ⏸ TKF100 2026-09-30: none of 1 holdings could be checked
              ⏸ TKF100 2026-09-30 iShares Developed World (IE00BFG1TM61): not verified — EODHD answered HTTP 404""",
            INVESTMENT,
            ERROR);
  }

  @Test
  void aFundWithNoSecuritiesAtAll_firesARedAlertAndNeverReadsAsOk() {
    notifier.notify(run(result(List.of(), List.of())));

    then(notificationService)
        .should()
        .sendMessage(
            """
            ⏸ Ownership limit check INCOMPLETE: month=2026-09
              ⏸ TKF100 2026-09-30: no securities found to check""",
            INVESTMENT,
            ERROR);
  }

  @Test
  void aRunThatFailedWithoutAMessage_namesTheExceptionInstead() {
    notifier.notifyFailed(MONTH, new NullPointerException());

    then(notificationService)
        .should()
        .sendMessage(
            "🛑 Ownership limit check FAILED: month=2026-09, error=NullPointerException — no underlying fund was checked",
            INVESTMENT,
            ERROR);
  }

  @Test
  void aFundThatCouldNotBeChecked_firesARedAlertNamingItsReason() {
    notifier.notify(
        new OwnershipCheckRun(
            MONTH,
            List.of(),
            List.of(
                new NotChecked(
                    TKF100, "no positions in 2026-09, the latest are from 2026-08-31"))));

    then(notificationService)
        .should()
        .sendMessage(
            """
            ⏸ Ownership limit check INCOMPLETE: month=2026-09
              ⏸ Not checked: TKF100 — no positions in 2026-09, the latest are from 2026-08-31""",
            INVESTMENT,
            ERROR);
  }

  @Test
  void aRunWithNoFundUnderAnOwnershipLimit_firesARedAlertThatNothingWasChecked() {
    notifier.notify(new OwnershipCheckRun(MONTH, List.of(), List.of()));

    then(notificationService)
        .should()
        .sendMessage(
            "⏸ Ownership limit check covered no fund: month=2026-09 — no fund has an ownership limit",
            INVESTMENT,
            ERROR);
  }

  @Test
  void aRunThatFailed_firesARedAlert() {
    notifier.notifyFailed(MONTH, new IllegalStateException("database unavailable"));

    then(notificationService)
        .should()
        .sendMessage(
            "🛑 Ownership limit check FAILED: month=2026-09, error=IllegalStateException: database unavailable — no underlying fund was checked",
            INVESTMENT,
            ERROR);
  }

  private static OwnershipCheckRun run(Result result) {
    return new OwnershipCheckRun(MONTH, List.of(result), List.of());
  }

  private static Result result(List<OwnershipBreach> holdings, List<UnverifiedHolding> unverified) {
    return new Result(TKF100, CHECK_DATE, holdings, unverified);
  }

  private static OwnershipBreach invescoEm(String percent, BreachSeverity severity) {
    return new OwnershipBreach(
        "IE00BMDBMY19",
        "Invesco EM",
        new BigDecimal("31950000"),
        new BigDecimal("150000000"),
        new BigDecimal("174000000"),
        "USD",
        LocalDate.of(2026, 10, 3),
        new BigDecimal(percent),
        new BigDecimal("20"),
        new BigDecimal("25"),
        severity);
  }

  private static OwnershipBreach canada(String percent) {
    return new OwnershipBreach(
        "LU0476289540",
        "Xtrackers Canada",
        new BigDecimal("2400000"),
        new BigDecimal("1200000000"),
        new BigDecimal("1200000000"),
        "EUR",
        LocalDate.of(2026, 10, 3),
        new BigDecimal(percent),
        new BigDecimal("20"),
        new BigDecimal("25"),
        OK);
  }

  private static UnverifiedHolding developedWorld(String reason) {
    return new UnverifiedHolding(
        "IE00BFG1TM61", "iShares Developed World", new BigDecimal("5000000"), reason);
  }
}
