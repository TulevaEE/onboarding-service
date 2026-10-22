package ee.tuleva.onboarding.investment.report.publishing;

import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK00;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import ee.tuleva.onboarding.investment.report.publishing.ReportPublication.NotLinked;
import ee.tuleva.onboarding.investment.report.publishing.ReportPublication.Published;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InvestmentReportPublicationCheckJobTest {

  private static final ZoneId TALLINN = ZoneId.of("Europe/Tallinn");
  private static final YearMonth SEPTEMBER = YearMonth.of(2026, 9);
  private static final LocalDate OCTOBER_10 = LocalDate.of(2026, 10, 10);
  private static final List<ReportPublication> ALL_PUBLISHED =
      List.of(
          new Published(TUK75, "https://tuleva.ee/wp-content/uploads/2026/10/a-2026-09.pdf"),
          new Published(TUK00, "https://tuleva.ee/wp-content/uploads/2026/10/b-2026-09.pdf"));
  private static final List<ReportPublication> ONE_MISSING =
      List.of(
          new Published(TUK75, "https://tuleva.ee/wp-content/uploads/2026/10/a-2026-09.pdf"),
          new NotLinked(TUK00));

  @Mock private InvestmentReportPublicationCheck check;
  @Mock private InvestmentReportPublicationNotifier notifier;

  @Test
  void beforeTheTenth_keepsPostingWhileTheMonthBeforeLastIsStillMissing() {
    var august = YearMonth.of(2026, 8);
    given(check.check(august)).willReturn(ONE_MISSING);

    jobOn(OCTOBER_10.minusDays(1)).checkTheLatestDueReportsArePublished();

    then(notifier).should().notify(august, ONE_MISSING, OCTOBER_10.minusDays(1));
  }

  @Test
  void beforeTheTenth_staysSilentWhileTheMonthBeforeLastIsPublished() {
    given(check.check(YearMonth.of(2026, 8))).willReturn(ALL_PUBLISHED);

    jobOn(OCTOBER_10.minusDays(1)).checkTheLatestDueReportsArePublished();

    then(notifier).shouldHaveNoInteractions();
  }

  @Test
  void onTheTenth_postsTheResultEvenWhenEveryReportIsPublished() {
    given(check.check(SEPTEMBER)).willReturn(ALL_PUBLISHED);

    jobOn(OCTOBER_10).checkTheLatestDueReportsArePublished();

    then(notifier).should().notify(SEPTEMBER, ALL_PUBLISHED, OCTOBER_10);
  }

  @Test
  void onTheTenth_postsTheMissingReports() {
    given(check.check(SEPTEMBER)).willReturn(ONE_MISSING);

    jobOn(OCTOBER_10).checkTheLatestDueReportsArePublished();

    then(notifier).should().notify(SEPTEMBER, ONE_MISSING, OCTOBER_10);
  }

  @Test
  void afterTheTenth_keepsPostingEveryDayWhileAReportIsMissing() {
    given(check.check(SEPTEMBER)).willReturn(ONE_MISSING);

    jobOn(LocalDate.of(2026, 10, 16)).checkTheLatestDueReportsArePublished();

    then(notifier).should().notify(SEPTEMBER, ONE_MISSING, LocalDate.of(2026, 10, 16));
  }

  @Test
  void afterTheTenth_staysSilentOnceEveryReportIsPublished() {
    given(check.check(SEPTEMBER)).willReturn(ALL_PUBLISHED);

    jobOn(OCTOBER_10.plusDays(1)).checkTheLatestDueReportsArePublished();

    then(notifier).shouldHaveNoInteractions();
  }

  @Test
  void fromTheTenthOfJanuary_requiresDecembersReports() {
    var december = YearMonth.of(2026, 12);
    given(check.check(december)).willReturn(ONE_MISSING);

    jobOn(LocalDate.of(2027, 1, 10)).checkTheLatestDueReportsArePublished();

    then(notifier).should().notify(december, ONE_MISSING, LocalDate.of(2027, 1, 10));
  }

  @Test
  void earlyInJanuary_stillRequiresNovembersReports() {
    var november = YearMonth.of(2026, 11);
    given(check.check(november)).willReturn(ONE_MISSING);

    jobOn(LocalDate.of(2027, 1, 5)).checkTheLatestDueReportsArePublished();

    then(notifier).should().notify(november, ONE_MISSING, LocalDate.of(2027, 1, 5));
  }

  private InvestmentReportPublicationCheckJob jobOn(LocalDate today) {
    var clock = Clock.fixed(today.atTime(9, 0).atZone(TALLINN).toInstant(), TALLINN);
    return new InvestmentReportPublicationCheckJob(check, notifier, clock);
  }
}
