package ee.tuleva.onboarding.investment.report.publishing;

import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.fund.TulevaFund;
import java.time.YearMonth;
import org.junit.jupiter.api.Test;

class FundReportMappingTest {

  @Test
  void forFundReturnsCorrectMapping() {
    assertThat(FundReportMapping.forFund(TulevaFund.TUK75)).isEqualTo(FundReportMapping.TUK75);
    assertThat(FundReportMapping.forFund(TulevaFund.TKF100)).isEqualTo(FundReportMapping.TKF100);
  }

  @Test
  void allReturnsFourMappings() {
    assertThat(FundReportMapping.all()).hasSize(4);
  }

  @Test
  void estonianMonthReturnsCorrectNames() {
    assertThat(FundReportMapping.estonianMonth(1)).isEqualTo("jaanuar");
    assertThat(FundReportMapping.estonianMonth(3)).isEqualTo("märts");
    assertThat(FundReportMapping.estonianMonth(12)).isEqualTo("detsember");
  }

  @Test
  void buildPdfFilenameFormatsCorrectly() {
    var filename = FundReportMapping.TUK75.buildPdfFilename(YearMonth.of(2026, 3));
    assertThat(filename)
        .isEqualTo("Tuleva Maailma Aktsiate Pensionifondi investeeringute aruanne 2026-03.pdf");
  }

  @Test
  void buildPdfFilenameFormatsSingleDigitMonth() {
    var filename = FundReportMapping.TUK00.buildPdfFilename(YearMonth.of(2026, 1));
    assertThat(filename).contains("2026-01.pdf");
  }

  @Test
  void tkf100ExcludedFromEmail() {
    assertThat(FundReportMapping.TKF100.includeInEmail()).isFalse();
    assertThat(FundReportMapping.TKF100.pageSlug()).isNotNull();
  }

  @Test
  void tuk75IncludedInEmail() {
    assertThat(FundReportMapping.TUK75.includeInEmail()).isTrue();
    assertThat(FundReportMapping.TUK75.pageSlug()).isNotNull();
  }

  @Test
  void fundAccessorsReturnCorrectValues() {
    assertThat(FundReportMapping.TUK75.fund()).isEqualTo(TulevaFund.TUK75);
    assertThat(FundReportMapping.TUK75.titleGenitive())
        .isEqualTo("Tuleva Maailma Aktsiate Pensionifondi");
    assertThat(FundReportMapping.TUK75.sectionHeading()).isEqualTo("Aktsiafondid");
  }
}
