package ee.tuleva.onboarding.kyb.screener;

import static ee.tuleva.onboarding.kyb.KybCheckType.ANNUAL_REPORTS;
import static ee.tuleva.onboarding.kyb.KybTestFixtures.companyWith;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.ariregister.AnnualReport;
import ee.tuleva.onboarding.ariregister.FinancialYear;
import ee.tuleva.onboarding.kyb.KybCheck;
import ee.tuleva.onboarding.kyb.RegisterHistory;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AnnualReportsScreenerTest {

  private final AnnualReportsScreener screener = new AnnualReportsScreener();

  @Test
  void recordsTheFinancialYearsAndTheFiledAnnualReportsInOrder() {
    var current = new FinancialYear("01.01", "31.12", LocalDate.of(2021, 5, 5), null);
    var earlier =
        new FinancialYear("01.07", "30.06", LocalDate.of(2019, 3, 1), LocalDate.of(2021, 5, 4));
    var latest = new AnnualReport(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31));
    var first = new AnnualReport(LocalDate.of(2019, 3, 1), LocalDate.of(2020, 6, 30));

    var result =
        screener.screen(
            companyWith(
                new RegisterHistory(List.of(), List.of(current, earlier), List.of(latest, first))));

    assertThat(result)
        .containsExactly(
            new KybCheck(
                ANNUAL_REPORTS,
                true,
                Map.of(
                    "financialYears",
                    List.of(
                        Map.of(
                            "start",
                            "01.07",
                            "end",
                            "30.06",
                            "validFrom",
                            "2019-03-01",
                            "validTo",
                            "2021-05-04"),
                        Map.of("start", "01.01", "end", "31.12", "validFrom", "2021-05-05")),
                    "annualReports",
                    List.of(
                        Map.of("periodStart", "2019-03-01", "periodEnd", "2020-06-30"),
                        Map.of("periodStart", "2024-01-01", "periodEnd", "2024-12-31")))));
  }

  @Test
  void recordsNoReportsForACompanyThatHasFiledNone() {
    var result = screener.screen(companyWith(new RegisterHistory(List.of(), List.of(), List.of())));

    assertThat(result)
        .containsExactly(
            new KybCheck(
                ANNUAL_REPORTS,
                true,
                Map.of("financialYears", List.of(), "annualReports", List.of())));
  }

  @Test
  void leavesOutTheKeysOfWhatTheRegisterDoesNotGive() {
    var result =
        screener.screen(
            companyWith(
                new RegisterHistory(
                    List.of(),
                    List.of(new FinancialYear(null, "31.12", null, null)),
                    List.of(new AnnualReport(null, LocalDate.of(2024, 12, 31))))));

    assertThat(result)
        .containsExactly(
            new KybCheck(
                ANNUAL_REPORTS,
                true,
                Map.of(
                    "financialYears",
                    List.of(Map.of("end", "31.12")),
                    "annualReports",
                    List.of(Map.of("periodEnd", "2024-12-31")))));
  }
}
