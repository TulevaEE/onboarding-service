package ee.tuleva.onboarding.investment.kid;

import static ee.tuleva.onboarding.investment.kid.ScenarioKind.FAVOURABLE;
import static ee.tuleva.onboarding.investment.kid.ScenarioKind.MODERATE;
import static ee.tuleva.onboarding.investment.kid.ScenarioKind.STRESS;
import static ee.tuleva.onboarding.investment.kid.ScenarioKind.UNFAVOURABLE;
import static ee.tuleva.onboarding.investment.kid.TransactionCostSource.PUBLISHED_OCF;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Severity.INFO;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Severity.WARNING;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TKF100;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class KidCalculationDigestTest {

  private static final int HOLDING_PERIOD_YEARS = 5;

  @Test
  void showsEachStoredMonthTheMonthsReadyToPublishAndOneNextLinePerFix() {
    var digest =
        new KidCalculationDigest(
            TKF100,
            HOLDING_PERIOD_YEARS,
            List.of(
                complete(45, YearMonth.of(2026, 5), "0.00321500"),
                refused(
                    46,
                    YearMonth.of(2026, 6),
                    "No OCF in force at the month end: fund=TKF100, monthEnd=2026-06-30"),
                refused(
                    47,
                    YearMonth.of(2026, 7),
                    "No OCF in force at the month end: fund=TKF100, monthEnd=2026-07-31"),
                refused(
                    48,
                    YearMonth.of(2026, 8),
                    "Daily closes missing in the risk history: after=2023-02-28,"
                        + " next=2023-03-21"),
                refused(
                    49,
                    YearMonth.of(2026, 9),
                    "KID series stopped: fund=TKF100, latestClose=2026-10-08",
                    "No OCF in force at the month end: fund=TKF100, monthEnd=2026-09-30")),
            Map.of(
                45L,
                List.of(
                    printed(1, STRESS, "6600"),
                    printed(5, STRESS, "5300"),
                    printed(5, UNFAVOURABLE, "8600"),
                    printed(5, MODERATE, "11500"),
                    printed(5, FAVOURABLE, "19200"))),
            List.of(
                complete(44, YearMonth.of(2026, 4), "0.00321500"),
                complete(45, YearMonth.of(2026, 5), "0.00321500")),
            List.of());

    assertThat(digest.text())
        .isEqualTo(
            """
            TKF100 põhiteabe kuu stsenaariumid
            ✅ 2026-05, arvutus 45: jooksvad tasud 0,3215%; 5 a: Stress 5300 €, Ebasoodne 8600 €,\
             Mõõdukas 11500 €, Soodne 19200 €
            🔴 2026-06, arvutus 46: No OCF in force at the month end: fund=TKF100,\
             monthEnd=2026-06-30
            🔴 2026-07, arvutus 47: No OCF in force at the month end: fund=TKF100,\
             monthEnd=2026-07-31
            🔴 2026-08, arvutus 48: Daily closes missing in the risk history:\
             after=2023-02-28, next=2023-03-21
            🔴 2026-09, arvutus 49: KID series stopped: fund=TKF100, latestClose=2026-10-08;\
             No OCF in force at the month end: fund=TKF100, monthEnd=2026-09-30
            Avaldamata valmis kuud: 2026-04 (arvutus 44), 2026-05 (arvutus 45)
            Next: kontrolli ja avalda: agent-gateway `kid.publish` KID_FUND=TKF100\
             CALCULATION_IDS=44,45
            Next: 2026-06, 2026-07, 2026-09: kanna KID-i jooksvate tasude näitaja agent-gateway\
             `ocf.publish_given` kaudu
            Next: 2026-08: kontrolli KID-i hinnaseeriat põhjuses nimetatud kuupäevadel;\
             parandatud andmetega arvutab töö kuu järgmisel tööpäeval uuesti
            Next: 2026-09: kontrolli KID-i hinnaseeria voogu""");
    assertThat(digest.severity()).isEqualTo(WARNING);
  }

  @Test
  void aRunThatOnlyCalculatedAMonthAsksForItsPublicationAndShowsTheRateWithAtLeastTwoDecimals() {
    var digest =
        new KidCalculationDigest(
            TKF100,
            HOLDING_PERIOD_YEARS,
            List.of(complete(50, YearMonth.of(2026, 9), "0.00500000")),
            Map.of(50L, List.of(printed(5, MODERATE, "11500"))),
            List.of(complete(50, YearMonth.of(2026, 9), "0.00500000")),
            List.of());

    assertThat(digest.text())
        .isEqualTo(
            """
            TKF100 põhiteabe kuu stsenaariumid
            ✅ 2026-09, arvutus 50: jooksvad tasud 0,50%; 5 a: Mõõdukas 11500 €
            Avaldamata valmis kuud: 2026-09 (arvutus 50)
            Next: kontrolli ja avalda: agent-gateway `kid.publish` KID_FUND=TKF100\
             CALCULATION_IDS=50""");
    assertThat(digest.severity()).isEqualTo(INFO);
  }

  @Test
  void aReminderWithNothingStoredListsOnlyTheMonthsWaitingForPublication() {
    var digest =
        new KidCalculationDigest(
            TKF100,
            HOLDING_PERIOD_YEARS,
            List.of(),
            Map.of(),
            List.of(
                complete(44, YearMonth.of(2026, 4), "0.00321500"),
                complete(45, YearMonth.of(2026, 5), "0.00321500")),
            List.of());

    assertThat(digest.text())
        .isEqualTo(
            """
            TKF100 põhiteabe kuu stsenaariumid
            Avaldamata valmis kuud: 2026-04 (arvutus 44), 2026-05 (arvutus 45)
            Next: kontrolli ja avalda: agent-gateway `kid.publish` KID_FUND=TKF100\
             CALCULATION_IDS=44,45""");
    assertThat(digest.severity()).isEqualTo(INFO);
  }

  @Test
  void aRunThatOnlyRefusedAMonthWhenNothingAwaitsPublicationAsksOnlyForTheFix() {
    var digest =
        new KidCalculationDigest(
            TKF100,
            HOLDING_PERIOD_YEARS,
            List.of(
                refused(51, YearMonth.of(2026, 10), "No prices for the KID series: fund=TKF100")),
            Map.of(),
            List.of(),
            List.of());

    assertThat(digest.text())
        .isEqualTo(
            """
            TKF100 põhiteabe kuu stsenaariumid
            🔴 2026-10, arvutus 51: No prices for the KID series: fund=TKF100
            Next: 2026-10: kontrolli KID-i hinnaseeria voogu""");
  }

  @Test
  void aMonthThatFailedUnexpectedlyGetsARedLineAndANextLineNamingItsLogEntry() {
    var digest =
        new KidCalculationDigest(
            TKF100,
            HOLDING_PERIOD_YEARS,
            List.of(),
            Map.of(),
            List.of(),
            List.of(new KidFailedMonth(YearMonth.of(2026, 1), "NumberFormatException")));

    assertThat(digest.text())
        .isEqualTo(
            """
            TKF100 põhiteabe kuu stsenaariumid
            🔴 2026-01: arvutus ei jooksnud: NumberFormatException
            Next: 2026-01: anna IT-le teada; logis on 'KID calculation failed: fund=TKF100, month=2026-01'""");
    assertThat(digest.severity()).isEqualTo(WARNING);
  }

  @Test
  void refusesToRenderACompleteMonthWithoutItsHoldingPeriodScenarios() {
    var digest =
        new KidCalculationDigest(
            TKF100,
            HOLDING_PERIOD_YEARS,
            List.of(complete(50, YearMonth.of(2026, 9), "0.00500000")),
            Map.of(50L, List.of(printed(1, MODERATE, "10200"))),
            List.of(),
            List.of());

    assertThatThrownBy(digest::text).isInstanceOf(IllegalStateException.class);
  }

  private static StoredKidCalculation complete(long id, YearMonth month, String ongoingCharges) {
    return new StoredKidCalculation(
        id,
        TKF100,
        month,
        1,
        List.of(),
        "MSCI_ACWI",
        month.atEndOfMonth(),
        new OcfBasis(3, LocalDate.of(2026, 1, 12), new BigDecimal(ongoingCharges), null),
        PUBLISHED_OCF,
        Instant.parse("2026-11-02T08:30:00Z"));
  }

  private static StoredKidCalculation refused(long id, YearMonth month, String... reasons) {
    return new StoredKidCalculation(
        id,
        TKF100,
        month,
        1,
        List.of(reasons),
        null,
        null,
        null,
        PUBLISHED_OCF,
        Instant.parse("2026-11-02T08:30:00Z"));
  }

  private static PrintedScenario printed(int horizonYears, ScenarioKind kind, String amount) {
    return new PrintedScenario(
        horizonYears, kind, new BigDecimal(amount), new BigDecimal("0.0100"));
  }
}
