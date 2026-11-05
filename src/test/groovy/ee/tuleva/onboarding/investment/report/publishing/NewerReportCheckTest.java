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
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NewerReportCheckTest {

  private static final YearMonth MARCH_2026 = YearMonth.of(2026, 3);
  private static final List<FundReportMapping> PENSION_FUND_REPORTS = List.of(TUK75, TUK00, TUV100);
  private static final Set<String> NOTHING_TO_REPLACE = Set.of();
  private static final String HAND_NAMED_FILE =
      "https://tuleva.ee/wp-content/uploads/2026/05/aruanne-aprill.pdf";
  private static final String ANOTHER_HAND_NAMED_FILE =
      "https://tuleva.ee/wp-content/uploads/2026/05/aruanne-aprill-parandatud.pdf";

  @Mock private WordPressPageReader pageReader;
  @InjectMocks private NewerReportCheck check;

  @Test
  void aPageLinkingALaterMonthsReportRefusesThePublish() {
    givenPagesLink(
        Optional.of(reportUrl(TUK75, YearMonth.of(2026, 2))),
        Optional.of(reportUrl(TUK00, YearMonth.of(2026, 4))),
        Optional.empty());

    assertThat(
            check
                .reasonsNotToPublish(PENSION_FUND_REPORTS, MARCH_2026, NOTHING_TO_REPLACE)
                .refusals())
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

    assertThat(
            check
                .reasonsNotToPublish(PENSION_FUND_REPORTS, MARCH_2026, NOTHING_TO_REPLACE)
                .refusals())
        .isEmpty();
  }

  @Test
  void aLinkedFileThatIsNotTheFundsReportRefusesThePublishSinceItCannotBeDated() {
    givenPagesLink(
        Optional.of(HAND_NAMED_FILE),
        Optional.of(reportUrl(TUK75, YearMonth.of(2026, 2))),
        Optional.of(reportUrl(TUV100, YearMonth.of(2026, 2))));

    assertThat(
            check
                .reasonsNotToPublish(PENSION_FUND_REPORTS, MARCH_2026, NOTHING_TO_REPLACE)
                .refusals())
        .containsExactly(
            unrecognisedFileRefusal("TUK75", HAND_NAMED_FILE),
            unrecognisedFileRefusal("TUK00", reportUrl(TUK75, YearMonth.of(2026, 2))));
  }

  @Test
  void onlyTheUnrecognisedFileTheAdminNamedIsReplaced() {
    givenPagesLink(
        Optional.of(HAND_NAMED_FILE),
        Optional.of(reportUrl(TUK75, YearMonth.of(2026, 2))),
        Optional.of(reportUrl(TUV100, YearMonth.of(2026, 2))));

    assertThat(
            check
                .reasonsNotToPublish(PENSION_FUND_REPORTS, MARCH_2026, Set.of(HAND_NAMED_FILE))
                .refusals())
        .containsExactly(unrecognisedFileRefusal("TUK00", reportUrl(TUK75, YearMonth.of(2026, 2))));
  }

  @Test
  void aNamedFileIsReplacedOnlyWhileThePageStillLinksIt() {
    givenPagesLink(
        Optional.of(ANOTHER_HAND_NAMED_FILE),
        Optional.of(reportUrl(TUK00, YearMonth.of(2026, 2))),
        Optional.empty());

    assertThat(
            check
                .reasonsNotToPublish(PENSION_FUND_REPORTS, MARCH_2026, Set.of(HAND_NAMED_FILE))
                .refusals())
        .containsExactly(
            unrecognisedFileRefusal("TUK75", ANOTHER_HAND_NAMED_FILE),
            replacesNothing(HAND_NAMED_FILE));
  }

  @Test
  void aNamedFileThatNoPageLinksRefusesThePublishRatherThanBeingIgnored() {
    givenPagesLink(
        Optional.of(reportUrl(TUK75, YearMonth.of(2026, 2))),
        Optional.of(reportUrl(TUK00, YearMonth.of(2026, 2))),
        Optional.empty());

    assertThat(
            check
                .reasonsNotToPublish(PENSION_FUND_REPORTS, MARCH_2026, Set.of(HAND_NAMED_FILE))
                .refusals())
        .containsExactly(replacesNothing(HAND_NAMED_FILE));
  }

  @Test
  void aNamedFileThatIsTheFundsOwnReportReplacesNothingAndRefusesThePublish() {
    var ownReport = reportUrl(TUK75, YearMonth.of(2026, 2));
    givenPagesLink(Optional.of(ownReport), Optional.empty(), Optional.empty());

    assertThat(
            check
                .reasonsNotToPublish(PENSION_FUND_REPORTS, MARCH_2026, Set.of(ownReport))
                .refusals())
        .containsExactly(replacesNothing(ownReport));
  }

  @Test
  void theCheckRecordsTheReportEachPageLinksBeforeTheUpload() {
    givenPagesLink(
        Optional.of(reportUrl(TUK75, MARCH_2026)), Optional.of(HAND_NAMED_FILE), Optional.empty());

    assertThat(
            check
                .reasonsNotToPublish(PENSION_FUND_REPORTS, MARCH_2026, Set.of(HAND_NAMED_FILE))
                .linkedReports())
        .isEqualTo(Map.of(TUK75, reportUrl(TUK75, MARCH_2026), TUK00, HAND_NAMED_FILE));
  }

  @Test
  void theCheckBeforeARepointLooksOnlyAtThatPage() {
    given(pageReader.investmentReportUrl(TUK75.pageSlug()))
        .willReturn(Optional.of(HAND_NAMED_FILE));

    assertThat(
            check.reasonsNotToRepoint(
                TUK75, MARCH_2026, Set.of(HAND_NAMED_FILE, ANOTHER_HAND_NAMED_FILE)))
        .isEmpty();
  }

  @Test
  void aNewerReportStillRefusesThePublishWhenTheAdminNamesIt() {
    var newerReport = reportUrl(TUK00, YearMonth.of(2026, 4));
    givenPagesLink(Optional.of(HAND_NAMED_FILE), Optional.of(newerReport), Optional.empty());

    assertThat(
            check
                .reasonsNotToPublish(
                    PENSION_FUND_REPORTS, MARCH_2026, Set.of(HAND_NAMED_FILE, newerReport))
                .refusals())
        .containsExactly(
            "Fund page already links a newer report: fund=TUK00, linked=2026-04,"
                + " publishing=2026-03",
            replacesNothing(newerReport));
  }

  @Test
  void aPageThatCannotBeReadRefusesThePublishAndTheOthersAreStillChecked() {
    given(pageReader.investmentReportUrl(TUK75.pageSlug()))
        .willReturn(Optional.of(reportUrl(TUK75, YearMonth.of(2026, 5))));
    given(pageReader.investmentReportUrl(TUK00.pageSlug()))
        .willThrow(new IllegalStateException("page unreadable"));
    given(pageReader.investmentReportUrl(TUV100.pageSlug())).willReturn(Optional.empty());

    assertThat(
            check
                .reasonsNotToPublish(PENSION_FUND_REPORTS, MARCH_2026, NOTHING_TO_REPLACE)
                .refusals())
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

  private static String replacesNothing(String named) {
    return "replaceUnrecognised names a file that no fund page links as an unrecognised file,"
        + " so nothing was published: value="
        + named;
  }

  private static String unrecognisedFileRefusal(String fundCode, String linked) {
    return "Fund page links a file that is not this fund's report, so it may be newer; check it,"
        + " then pass its URL as replaceUnrecognised to replace it: fund="
        + fundCode
        + ", linked="
        + linked
        + ", publishing=2026-03";
  }

  private static String reportUrl(FundReportMapping mapping, YearMonth month) {
    return "https://tuleva.ee/wp-content/uploads/2026/05/"
        + WordPressSlug.of(mapping.buildPdfFilename(month));
  }
}
