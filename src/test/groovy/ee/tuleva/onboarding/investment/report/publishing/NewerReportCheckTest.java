package ee.tuleva.onboarding.investment.report.publishing;

import static ee.tuleva.onboarding.investment.report.publishing.FundReportMapping.TUK00;
import static ee.tuleva.onboarding.investment.report.publishing.FundReportMapping.TUK75;
import static ee.tuleva.onboarding.investment.report.publishing.FundReportMapping.TUV100;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import ee.tuleva.onboarding.investment.report.publishing.wordpress.WordPressPageReader;
import ee.tuleva.onboarding.investment.report.publishing.wordpress.WordPressSlug;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NewerReportCheckTest {

  private static final YearMonth MARCH_2026 = YearMonth.of(2026, 3);
  private static final List<FundReportMapping> PENSION_FUND_REPORTS = List.of(TUK75, TUK00, TUV100);

  @Mock private WordPressPageReader pageReader;
  @InjectMocks private NewerReportCheck check;

  @Test
  void aPageLinkingALaterMonthsReportRefusesThePublish() {
    givenPagesLink(
        Optional.of(reportUrl(TUK75, YearMonth.of(2026, 2))),
        Optional.of(reportUrl(TUK00, YearMonth.of(2026, 4))),
        Optional.empty());

    assertThat(check.newerReportsLinked(PENSION_FUND_REPORTS, MARCH_2026))
        .containsExactly(
            "Fund page already links a newer report: fund=TUK00, linked=2026-04,"
                + " publishing=2026-03");
  }

  @Test
  void theSameMonthAnEarlierOneOrAFileThatIsNotTheFundsReportIsReplaced() {
    givenPagesLink(
        Optional.of(reportUrl(TUK75, MARCH_2026)),
        Optional.of(reportUrl(TUK00, YearMonth.of(2026, 1))),
        Optional.of(reportUrl(TUK75, YearMonth.of(2026, 4))));

    assertThat(check.newerReportsLinked(PENSION_FUND_REPORTS, MARCH_2026)).isEmpty();
  }

  @Test
  void aPageThatCannotBeReadRefusesThePublishAndTheOthersAreStillChecked() {
    given(pageReader.investmentReportUrl(TUK75.pageSlug()))
        .willReturn(Optional.of(reportUrl(TUK75, YearMonth.of(2026, 5))));
    given(pageReader.investmentReportUrl(TUK00.pageSlug()))
        .willThrow(new IllegalStateException("page unreadable"));
    given(pageReader.investmentReportUrl(TUV100.pageSlug())).willReturn(Optional.empty());

    assertThat(check.newerReportsLinked(PENSION_FUND_REPORTS, MARCH_2026))
        .containsExactly(
            "Fund page already links a newer report: fund=TUK75, linked=2026-05,"
                + " publishing=2026-03",
            "Could not read the fund page to check it links no newer report: fund=TUK00,"
                + " error=page unreadable");
  }

  private void givenPagesLink(
      Optional<String> tuk75Link, Optional<String> tuk00Link, Optional<String> tuv100Link) {
    given(pageReader.investmentReportUrl(TUK75.pageSlug())).willReturn(tuk75Link);
    given(pageReader.investmentReportUrl(TUK00.pageSlug())).willReturn(tuk00Link);
    given(pageReader.investmentReportUrl(TUV100.pageSlug())).willReturn(tuv100Link);
  }

  private static String reportUrl(FundReportMapping mapping, YearMonth month) {
    return "https://tuleva.ee/wp-content/uploads/2026/05/"
        + WordPressSlug.of(mapping.buildPdfFilename(month));
  }
}
