package ee.tuleva.onboarding.investment.fundpage;

import static ee.tuleva.onboarding.investment.report.publishing.wordpress.DisclosureGap.Page.FUND;
import static ee.tuleva.onboarding.investment.report.publishing.wordpress.DisclosureGap.Problem.MISSING;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TKF100;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK00;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUV100;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import ee.tuleva.onboarding.investment.fundpage.FundPageDisclosures.Complete;
import ee.tuleva.onboarding.investment.fundpage.FundPageDisclosures.Gaps;
import ee.tuleva.onboarding.investment.fundpage.FundPageDisclosures.NotChecked;
import ee.tuleva.onboarding.investment.report.publishing.wordpress.DisclosureGap;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FundPageDisclosureCheckJobTest {

  private static final List<FundPageDisclosures> ALL_COMPLETE =
      List.of(new Complete(TUK75), new Complete(TUK00), new Complete(TUV100), new Complete(TKF100));

  @Mock private FundPageDisclosureCheck check;
  @Mock private FundPageDisclosureNotifier notifier;
  @InjectMocks private FundPageDisclosureCheckJob job;

  @Test
  void everyFundPageComplete_postsNothing() {
    given(check.check()).willReturn(ALL_COMPLETE);

    job.checkTheFundPagesListEveryRequiredDocument();

    then(notifier).shouldHaveNoInteractions();
  }

  @Test
  void aFundPageWithAGap_postsTheResult() {
    List<FundPageDisclosures> oneGap =
        List.of(
            new Gaps(TUK75, List.of(new DisclosureGap("model_portfolio_file", MISSING, FUND))),
            new Complete(TUK00),
            new Complete(TUV100),
            new Complete(TKF100));
    given(check.check()).willReturn(oneGap);

    job.checkTheFundPagesListEveryRequiredDocument();

    then(notifier).should().notify(oneGap);
  }

  @Test
  void aFundPageThatCouldNotBeChecked_postsTheResult() {
    List<FundPageDisclosures> oneUnchecked =
        List.of(
            new Complete(TUK75),
            new Complete(TUK00),
            new NotChecked(TUV100, "IllegalStateException: no disclosure gaps over REST"),
            new Complete(TKF100));
    given(check.check()).willReturn(oneUnchecked);

    job.checkTheFundPagesListEveryRequiredDocument();

    then(notifier).should().notify(oneUnchecked);
  }
}
