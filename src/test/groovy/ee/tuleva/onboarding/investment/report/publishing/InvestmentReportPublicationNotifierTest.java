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
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InvestmentReportPublicationNotifierTest {

  private static final YearMonth SEPTEMBER = YearMonth.of(2026, 9);
  private static final String UPLOADS = "https://tuleva.ee/wp-content/uploads/";

  @Mock private OperationsNotificationService notificationService;
  @InjectMocks private InvestmentReportPublicationNotifier notifier;

  @Test
  void everyReportPublished_postsAGreenSummary() {
    notifier.notify(
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
  void aReportNotYetPublished_postsARedAlertNamingTheDeadline() {
    notifier.notify(
        SEPTEMBER,
        List.of(
            new Published(TUK75, UPLOADS + "2026/10/tuk75-aruanne-2026-09.pdf"),
            new Outdated(TUK00, UPLOADS + "2026/09/tuk00-aruanne-2026-08.pdf"),
            new NotLinked(TUV100)));

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
  void aPageThatCouldNotBeRead_postsAWarningWhenNoReportIsConfirmedMissing() {
    notifier.notify(
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

    assertThatCode(() -> notifier.notify(SEPTEMBER, List.of(new NotLinked(TUK75))))
        .doesNotThrowAnyException();
  }
}
