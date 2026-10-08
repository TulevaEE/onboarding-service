package ee.tuleva.onboarding.investment.report.publishing;

import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.INVESTMENT;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Severity.ERROR;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Severity.INFO;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Severity.WARNING;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK00;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUV100;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;

import ee.tuleva.onboarding.investment.report.publishing.ReportPublication.NotChecked;
import ee.tuleva.onboarding.investment.report.publishing.ReportPublication.NotLinked;
import ee.tuleva.onboarding.investment.report.publishing.ReportPublication.Outdated;
import ee.tuleva.onboarding.investment.report.publishing.ReportPublication.Published;
import ee.tuleva.onboarding.notification.OperationsNotificationService;
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
class InvestmentReportPublicationNotifierTest {

  private static final ZoneId TALLINN = ZoneId.of("Europe/Tallinn");
  private static final YearMonth SEPTEMBER = YearMonth.of(2026, 9);
  private static final LocalDate OCTOBER_10 = LocalDate.of(2026, 10, 10);
  private static final LocalDate OCTOBER_15 = LocalDate.of(2026, 10, 15);
  private static final String UPLOADS = "https://tuleva.ee/wp-content/uploads/";
  private static final List<ReportPublication> TWO_MISSING =
      List.of(
          new Published(TUK75, UPLOADS + "2026/10/tuk75-aruanne-2026-09.pdf"),
          new Outdated(TUK00, UPLOADS + "2026/09/tuk00-aruanne-2026-08.pdf"),
          new NotLinked(TUV100));

  @Mock private OperationsNotificationService notificationService;

  @Test
  void everyReportPublished_postsAGreenSummary() {
    notifierOn(OCTOBER_10)
        .notify(
            SEPTEMBER,
            List.of(
                new Published(TUK75, UPLOADS + "2026/10/tuk75-aruanne-2026-09.pdf"),
                new Published(TUK00, UPLOADS + "2026/10/tuk00-aruanne-2026-09.pdf"),
                new Published(TUV100, UPLOADS + "2026/10/tuv100-aruanne-2026-09.pdf")));

    then(notificationService)
        .should()
        .sendMessage(
            String.join(
                "\n",
                "✅ Investment reports for 2026-09 are published on tuleva.ee",
                "  ✅ TUK75: tuk75-aruanne-2026-09.pdf",
                "  ✅ TUK00: tuk00-aruanne-2026-09.pdf",
                "  ✅ TUV100: tuv100-aruanne-2026-09.pdf"),
            INVESTMENT,
            INFO);
  }

  @Test
  void aReportMissingBeforeTheFifteenth_postsAWarningNamingTheDeadline() {
    notifierOn(OCTOBER_15.minusDays(1)).notify(SEPTEMBER, TWO_MISSING);

    then(notificationService)
        .should()
        .sendMessage(
            String.join(
                "\n",
                "⚠️ Investment reports for 2026-09 are not all published on tuleva.ee"
                    + " — due 2026-10-15",
                "  ✅ TUK75: tuk75-aruanne-2026-09.pdf",
                "  ⚠️ TUK00: the fund page still links tuk00-aruanne-2026-08.pdf",
                "  ⚠️ TUV100: the fund page links no investment report"),
            INVESTMENT,
            WARNING);
  }

  @Test
  void aReportStillMissingOnTheFifteenth_turnsRed() {
    notifierOn(OCTOBER_15).notify(SEPTEMBER, TWO_MISSING);

    then(notificationService)
        .should()
        .sendMessage(
            String.join(
                "\n",
                "🔴 Investment reports for 2026-09 are not all published on tuleva.ee"
                    + " — due 2026-10-15",
                "  ✅ TUK75: tuk75-aruanne-2026-09.pdf",
                "  🔴 TUK00: the fund page still links tuk00-aruanne-2026-08.pdf",
                "  🔴 TUV100: the fund page links no investment report"),
            INVESTMENT,
            ERROR);
  }

  @Test
  void aReportStillMissingIntoTheMonthAfter_staysRed() {
    notifierOn(LocalDate.of(2026, 11, 3)).notify(SEPTEMBER, List.of(new NotLinked(TUK75)));

    then(notificationService)
        .should()
        .sendMessage(
            String.join(
                "\n",
                "🔴 Investment reports for 2026-09 are not all published on tuleva.ee"
                    + " — due 2026-10-15",
                "  🔴 TUK75: the fund page links no investment report"),
            INVESTMENT,
            ERROR);
  }

  @Test
  void aPageThatCouldNotBeRead_postsAWarningWhenNoReportIsConfirmedMissing() {
    notifierOn(OCTOBER_15)
        .notify(
            SEPTEMBER,
            List.of(
                new NotChecked(TUK75, "HttpClientErrorException: 404 Not Found"),
                new Published(TUK00, UPLOADS + "2026/10/tuk00-aruanne-2026-09.pdf"),
                new Published(TUV100, UPLOADS + "2026/10/tuv100-aruanne-2026-09.pdf")));

    then(notificationService)
        .should()
        .sendMessage(
            String.join(
                "\n",
                "⏸ Could not confirm the investment reports for 2026-09 on tuleva.ee"
                    + " — due 2026-10-15",
                "  ⏸ TUK75: could not check — HttpClientErrorException: 404 Not Found",
                "  ✅ TUK00: tuk00-aruanne-2026-09.pdf",
                "  ✅ TUV100: tuv100-aruanne-2026-09.pdf"),
            INVESTMENT,
            WARNING);
  }

  @Test
  void aSlackFailure_isNotThrownToTheJob() {
    willThrow(new IllegalStateException("No webhook for slack channel INVESTMENT"))
        .given(notificationService)
        .sendMessage(any(), any(), any());

    assertThatCode(() -> notifierOn(OCTOBER_10).notify(SEPTEMBER, List.of(new NotLinked(TUK75))))
        .doesNotThrowAnyException();
  }

  private InvestmentReportPublicationNotifier notifierOn(LocalDate today) {
    var clock = Clock.fixed(today.atTime(9, 0).atZone(TALLINN).toInstant(), TALLINN);
    return new InvestmentReportPublicationNotifier(notificationService, clock);
  }
}
