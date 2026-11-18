package ee.tuleva.onboarding.investment.transaction;

import static ee.tuleva.onboarding.investment.report.ReportType.R17_PEVA;
import static ee.tuleva.onboarding.investment.report.ReportType.R21_RAVA;
import static ee.tuleva.onboarding.investment.report.ReportType.R45;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ee.tuleva.onboarding.investment.transaction.InputGateFailure.MissingPevaRavaReports;
import ee.tuleva.onboarding.investment.transaction.InputGateFailure.MissingR16;
import ee.tuleva.onboarding.investment.transaction.InputGateFailure.MissingR45;
import ee.tuleva.onboarding.investment.transaction.InputGateFailure.StalePositionReport;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.Test;

class InputGateFailureTest {

  @Test
  void aStalePositionReportNamesTheReportFoundAndTheOneNeeded() {
    assertThat(
            new StalePositionReport(LocalDate.of(2026, 11, 13), LocalDate.of(2026, 11, 17))
                .slackLine())
        .isEqualTo(
            "SEB positsiooniraport on 2026-11-13, vaja 2026-11-17: küsi 2026-11-17 raport SEB-lt");
  }

  @Test
  void aFundWithoutAnyPositionReportSaysItIsMissing() {
    assertThat(new StalePositionReport(null, LocalDate.of(2026, 11, 17)).slackLine())
        .isEqualTo("SEB positsiooniraport puudub, vaja 2026-11-17: küsi 2026-11-17 raport SEB-lt");
  }

  @Test
  void aMissingR45WithoutAnEarlierOneLeavesTheLatestOut() {
    assertThat(new MissingR45(LocalDate.of(2026, 11, 18), null).slackLine())
        .isEqualTo("R45 2026-11-18 puudub: lae R45 EPIS-ist");
  }

  @Test
  void bothMissingPevaRavaReportsAreNamedTogether() {
    assertThat(
            new MissingPevaRavaReports(
                    List.of(R17_PEVA, R21_RAVA),
                    LocalDate.of(2027, 1, 4),
                    LocalDate.of(2026, 12, 22))
                .slackLine())
        .isEqualTo(
            "R17 ja R21 puuduvad (PEVA/RAVA täitmine 2027-01-04, vood arvesse alates 2026-12-22):"
                + " lae EPIS-ist");
  }

  @Test
  void aSingleMissingPevaRavaReportIsNamedAlone() {
    assertThat(
            new MissingPevaRavaReports(
                    List.of(R17_PEVA), LocalDate.of(2027, 1, 4), LocalDate.of(2026, 12, 14))
                .slackLine())
        .isEqualTo(
            "R17 puudub (PEVA/RAVA täitmine 2027-01-04, vood arvesse alates 2026-12-14):"
                + " lae EPIS-ist");
  }

  @Test
  void onlyPevaRavaReportTypesCanBeMissingFromACycle() {
    var failure =
        new MissingPevaRavaReports(
            List.of(R45), LocalDate.of(2027, 1, 4), LocalDate.of(2026, 12, 14));

    assertThatThrownBy(failure::slackLine).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void aMissingR16NamesTheMonthItsPaymentDeadlineAndWhenTradePrepCountsIt() {
    assertThat(
            new MissingR16(
                    YearMonth.of(2026, 11), LocalDate.of(2026, 11, 16), LocalDate.of(2026, 11, 9))
                .slackLine())
        .isEqualTo(
            "R16 2026-11 puudub (väljamaksed 2026-11-16, arvesse alates 2026-11-09): lae R16"
                + " EPIS-ist");
  }
}
