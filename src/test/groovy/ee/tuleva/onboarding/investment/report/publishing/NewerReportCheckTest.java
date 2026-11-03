package ee.tuleva.onboarding.investment.report.publishing;

import static ee.tuleva.onboarding.investment.report.publishing.FundReportMapping.TUK00;
import static ee.tuleva.onboarding.investment.report.publishing.FundReportMapping.TUK75;
import static ee.tuleva.onboarding.investment.report.publishing.FundReportMapping.TUV100;
import static ee.tuleva.onboarding.investment.report.publishing.NewerReportCheck.UnrecognisedFile.REFUSE;
import static ee.tuleva.onboarding.investment.report.publishing.NewerReportCheck.UnrecognisedFile.REPLACE;
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
  private static final String HAND_NAMED_FILE =
      "https://tuleva.ee/wp-content/uploads/2026/05/aruanne-aprill.pdf";

  @Mock private WordPressPageReader pageReader;
  @InjectMocks private NewerReportCheck check;

  @Test
  void aPageLinkingALaterMonthsReportRefusesThePublish() {
    givenPagesLink(
        Optional.of(reportUrl(TUK75, YearMonth.of(2026, 2))),
        Optional.of(reportUrl(TUK00, YearMonth.of(2026, 4))),
        Optional.empty());

    assertThat(check.reasonsNotToReplace(PENSION_FUND_REPORTS, MARCH_2026, REFUSE))
        .containsExactly(
            "Fund page already links a newer report: fund=TUK00, linked=2026-04,"
                + " publishing=2026-03");
  }

  @Test
  void theSameMonthOrAnEarlierOneIsReplaced() {
    givenPagesLink(
        Optional.of(reportUrl(TUK75, MARCH_2026)),
        Optional.of(reportUrl(TUK00, YearMonth.of(2026, 1))),
        Optional.empty());

    assertThat(check.reasonsNotToReplace(PENSION_FUND_REPORTS, MARCH_2026, REFUSE)).isEmpty();
  }

  @Test
  void aLinkedFileThatIsNotTheFundsReportRefusesThePublishSinceItCannotBeDated() {
    givenPagesLink(
        Optional.of(HAND_NAMED_FILE),
        Optional.of(reportUrl(TUK75, YearMonth.of(2026, 2))),
        Optional.of(reportUrl(TUV100, YearMonth.of(2026, 2))));

    assertThat(check.reasonsNotToReplace(PENSION_FUND_REPORTS, MARCH_2026, REFUSE))
        .containsExactly(
            "Fund page links a file that is not this fund's report, so it may be newer; check it,"
                + " then publish with replaceUnrecognised=true to replace it: fund=TUK75,"
                + " linked="
                + HAND_NAMED_FILE
                + ", publishing=2026-03",
            "Fund page links a file that is not this fund's report, so it may be newer; check it,"
                + " then publish with replaceUnrecognised=true to replace it: fund=TUK00,"
                + " linked="
                + reportUrl(TUK75, YearMonth.of(2026, 2))
                + ", publishing=2026-03");
  }

  @Test
  void aLinkedFileThatIsNotTheFundsReportIsReplacedOnlyWhenTheAdminAsksForIt() {
    givenPagesLink(
        Optional.of(HAND_NAMED_FILE),
        Optional.of(reportUrl(TUK75, YearMonth.of(2026, 2))),
        Optional.of(reportUrl(TUV100, YearMonth.of(2026, 2))));

    assertThat(check.reasonsNotToReplace(PENSION_FUND_REPORTS, MARCH_2026, REPLACE)).isEmpty();
  }

  @Test
  void aNewerReportStillRefusesThePublishWhenUnrecognisedFilesMayBeReplaced() {
    givenPagesLink(
        Optional.of(HAND_NAMED_FILE),
        Optional.of(reportUrl(TUK00, YearMonth.of(2026, 4))),
        Optional.empty());

    assertThat(check.reasonsNotToReplace(PENSION_FUND_REPORTS, MARCH_2026, REPLACE))
        .containsExactly(
            "Fund page already links a newer report: fund=TUK00, linked=2026-04,"
                + " publishing=2026-03");
  }

  @Test
  void aPageThatCannotBeReadRefusesThePublishAndTheOthersAreStillChecked() {
    given(pageReader.investmentReportUrl(TUK75.pageSlug()))
        .willReturn(Optional.of(reportUrl(TUK75, YearMonth.of(2026, 5))));
    given(pageReader.investmentReportUrl(TUK00.pageSlug()))
        .willThrow(new IllegalStateException("page unreadable"));
    given(pageReader.investmentReportUrl(TUV100.pageSlug())).willReturn(Optional.empty());

    assertThat(check.reasonsNotToReplace(PENSION_FUND_REPORTS, MARCH_2026, REPLACE))
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
