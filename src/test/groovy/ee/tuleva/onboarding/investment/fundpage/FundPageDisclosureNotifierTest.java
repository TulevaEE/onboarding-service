package ee.tuleva.onboarding.investment.fundpage;

import static ee.tuleva.onboarding.investment.report.publishing.wordpress.DisclosureGap.Page.FUND;
import static ee.tuleva.onboarding.investment.report.publishing.wordpress.DisclosureGap.Page.SHARED;
import static ee.tuleva.onboarding.investment.report.publishing.wordpress.DisclosureGap.Problem.MISSING;
import static ee.tuleva.onboarding.investment.report.publishing.wordpress.DisclosureGap.Problem.NO_EFFECTIVE_DATE;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.INVESTMENT;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Severity.ERROR;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Severity.WARNING;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TKF100;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK00;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUV100;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import ee.tuleva.onboarding.investment.fundpage.FundPageDisclosures.Complete;
import ee.tuleva.onboarding.investment.fundpage.FundPageDisclosures.Gaps;
import ee.tuleva.onboarding.investment.fundpage.FundPageDisclosures.NotChecked;
import ee.tuleva.onboarding.investment.report.publishing.wordpress.DisclosureGap;
import ee.tuleva.onboarding.notification.OperationsNotificationService;
import ee.tuleva.onboarding.notification.OperationsNotificationService.Channel;
import ee.tuleva.onboarding.notification.OperationsNotificationService.Severity;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class FundPageDisclosureNotifierTest {

  private static final DisclosureGap SUSTAINABILITY_RISKS_MISSING_ON_THE_SHARED_PAGE =
      new DisclosureGap("sustainability_risks_file", MISSING, SHARED);
  private static final DisclosureGap REMUNERATION_POLICY_MISSING_ON_THE_SHARED_PAGE =
      new DisclosureGap("remuneration_policy_file", MISSING, SHARED);

  private final RecordingNotificationService notifications = new RecordingNotificationService();
  private final FundPageDisclosureNotifier notifier = new FundPageDisclosureNotifier(notifications);

  @Test
  void aRequiredDocumentMissing_postsARedAlert_withTheSharedPageGapsOnceAndTheAction() {
    notifier.notify(
        List.of(
            new Gaps(
                TUK75,
                List.of(
                    new DisclosureGap("model_portfolio_file", MISSING, FUND),
                    SUSTAINABILITY_RISKS_MISSING_ON_THE_SHARED_PAGE,
                    REMUNERATION_POLICY_MISSING_ON_THE_SHARED_PAGE)),
            new Gaps(
                TUK00,
                List.of(
                    SUSTAINABILITY_RISKS_MISSING_ON_THE_SHARED_PAGE,
                    REMUNERATION_POLICY_MISSING_ON_THE_SHARED_PAGE)),
            new NotChecked(TUV100, "HttpClientErrorException: 404 Not Found"),
            new Gaps(
                TKF100,
                List.of(
                    SUSTAINABILITY_RISKS_MISSING_ON_THE_SHARED_PAGE,
                    REMUNERATION_POLICY_MISSING_ON_THE_SHARED_PAGE,
                    new DisclosureGap("terms_upcoming_file", NO_EFFECTIVE_DATE, FUND)))));

    assertThat(notifications.sent)
        .containsExactly(
            new Sent(
                String.join(
                    "\n",
                    "🔴 Required documents are missing from the fund pages on tuleva.ee",
                    "  🔴 TUK75: model_portfolio_file",
                    "  🔴 Shared documents page: sustainability_risks_file,"
                        + " remuneration_policy_file",
                    "  ⚠️ TKF100: terms_upcoming_file has no effective date in its filename",
                    "  ⏸ TUV100: not checked — HttpClientErrorException: 404 Not Found",
                    "Set them through agent-gateway fund_pages."),
                INVESTMENT,
                ERROR));
  }

  @Test
  void aDocumentMissingOnlyFromTheSharedPage_isRed_andListedOnceThoughEveryFundPageRepeatsIt() {
    notifier.notify(
        List.of(
            new Gaps(TUK75, List.of(SUSTAINABILITY_RISKS_MISSING_ON_THE_SHARED_PAGE)),
            new Gaps(TUK00, List.of(SUSTAINABILITY_RISKS_MISSING_ON_THE_SHARED_PAGE)),
            new Gaps(TUV100, List.of(SUSTAINABILITY_RISKS_MISSING_ON_THE_SHARED_PAGE)),
            new Gaps(TKF100, List.of(SUSTAINABILITY_RISKS_MISSING_ON_THE_SHARED_PAGE))));

    assertThat(notifications.sent)
        .containsExactly(
            new Sent(
                String.join(
                    "\n",
                    "🔴 Required documents are missing from the fund pages on tuleva.ee",
                    "  🔴 Shared documents page: sustainability_risks_file",
                    "Set them through agent-gateway fund_pages."),
                INVESTMENT,
                ERROR));
  }

  @Test
  void onlyFilenamesWithoutAnEffectiveDate_postAWarning_withTheAction() {
    var navProcedureUndatedOnTheSharedPage =
        new DisclosureGap("nav_procedure_file", NO_EFFECTIVE_DATE, SHARED);
    notifier.notify(
        List.of(
            new Gaps(
                TUK75,
                List.of(
                    new DisclosureGap("prospectus_file", NO_EFFECTIVE_DATE, FUND),
                    new DisclosureGap("terms_file", NO_EFFECTIVE_DATE, FUND),
                    navProcedureUndatedOnTheSharedPage)),
            new Gaps(TUK00, List.of(navProcedureUndatedOnTheSharedPage)),
            new Gaps(TUV100, List.of(navProcedureUndatedOnTheSharedPage)),
            new Gaps(
                TKF100,
                List.of(new DisclosureGap("terms_upcoming_file", NO_EFFECTIVE_DATE, FUND)))));

    assertThat(notifications.sent)
        .containsExactly(
            new Sent(
                String.join(
                    "\n",
                    "⚠️ A fund page document on tuleva.ee has no effective date in its filename",
                    "  ⚠️ TUK75: prospectus_file, terms_file have no effective date in their"
                        + " filenames",
                    "  ⚠️ TKF100: terms_upcoming_file has no effective date in its filename",
                    "  ⚠️ Shared documents page: nav_procedure_file has no effective date in its"
                        + " filename",
                    "Set them through agent-gateway fund_pages."),
                INVESTMENT,
                WARNING));
  }

  @Test
  void onlyPagesThatCouldNotBeRead_postAWarning_withoutTheAction() {
    notifier.notify(
        List.of(
            new NotChecked(TUK75, "HttpClientErrorException: 404 Not Found"),
            new Complete(TUK00),
            new Complete(TUV100),
            new NotChecked(TKF100, "IllegalStateException: no disclosure gaps over REST")));

    assertThat(notifications.sent)
        .containsExactly(
            new Sent(
                String.join(
                    "\n",
                    "⚠️ Could not check the fund pages' documents on tuleva.ee",
                    "  ⏸ TUK75: not checked — HttpClientErrorException: 404 Not Found",
                    "  ⏸ TKF100: not checked — IllegalStateException: no disclosure gaps over"
                        + " REST"),
                INVESTMENT,
                WARNING));
  }

  @Test
  void pagesWithNothingToReport_postNothing() {
    notifier.notify(
        List.of(
            new Complete(TUK75), new Complete(TUK00), new Complete(TUV100), new Complete(TKF100)));

    assertThat(notifications.sent).isEmpty();
  }

  @Test
  void aSlackFailure_isNotThrownToTheJob() {
    var unreachableSlack = new FundPageDisclosureNotifier(new UnreachableNotificationService());

    assertThatCode(
            () ->
                unreachableSlack.notify(
                    List.of(
                        new Gaps(TUK75, List.of(SUSTAINABILITY_RISKS_MISSING_ON_THE_SHARED_PAGE)))))
        .doesNotThrowAnyException();
  }

  private record Sent(String message, Channel channel, Severity severity) {}

  private static class RecordingNotificationService implements OperationsNotificationService {

    private final List<Sent> sent = new ArrayList<>();

    @Override
    public void sendMessage(String message, Channel channel) {
      throw new UnsupportedOperationException("The notifier always names a severity");
    }

    @Override
    public void sendMessage(String message, Channel channel, Severity severity) {
      sent.add(new Sent(message, channel, severity));
    }
  }

  private static class UnreachableNotificationService implements OperationsNotificationService {

    @Override
    public void sendMessage(String message, Channel channel) {
      throw new IllegalStateException("No webhook for slack channel INVESTMENT");
    }

    @Override
    public void sendMessage(String message, Channel channel, Severity severity) {
      sendMessage(message, channel);
    }
  }
}
