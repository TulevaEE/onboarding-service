package ee.tuleva.onboarding.investment.transaction.ingest;

import static ee.tuleva.onboarding.investment.report.ReportProvider.SEB;
import static ee.tuleva.onboarding.investment.report.ReportType.PENDING_TRANSACTIONS;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.investment.report.InvestmentReport;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class SettlementCheckTest {

  @Test
  void aReportThatPassedEveryCheck_givesItsAsOfDate() {
    assertThat(
            SettlementCheck.CHECKED.trustedAsOfDate(
                reportWithMetadata(Map.of("asOfDate", "2026-11-18"))))
        .contains(LocalDate.of(2026, 11, 18));
  }

  @ParameterizedTest
  @EnumSource(value = SettlementCheck.class, names = "CHECKED", mode = EnumSource.Mode.EXCLUDE)
  void aReportSkippedBeforeOrInsteadOfTheTruncationCheck_givesNoAsOfDate(SettlementCheck check) {
    assertThat(check.trustedAsOfDate(reportWithMetadata(Map.of("asOfDate", "2026-11-18"))))
        .isEmpty();
  }

  @Test
  void aTrustedReportWhoseAsOfDateCannotBeRead_givesNoAsOfDate() {
    assertThat(SettlementCheck.CHECKED.trustedAsOfDate(reportWithMetadata(Map.of()))).isEmpty();
  }

  private static InvestmentReport reportWithMetadata(Map<String, Object> metadata) {
    return InvestmentReport.builder()
        .provider(SEB)
        .reportType(PENDING_TRANSACTIONS)
        .reportDate(LocalDate.of(2026, 11, 19))
        .metadata(metadata)
        .rawData(List.of())
        .build();
  }
}
