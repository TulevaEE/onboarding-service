package ee.tuleva.onboarding.investment.fundpage;

import ee.tuleva.onboarding.investment.report.publishing.wordpress.DisclosureGap;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.util.List;

sealed interface FundPageDisclosures {

  TulevaFund fund();

  static FundPageDisclosures listing(TulevaFund fund, List<DisclosureGap> gaps) {
    return gaps.isEmpty() ? new Complete(fund) : new Gaps(fund, gaps);
  }

  static boolean allComplete(List<FundPageDisclosures> pages) {
    return pages.stream().allMatch(Complete.class::isInstance);
  }

  record Complete(TulevaFund fund) implements FundPageDisclosures {}

  record Gaps(TulevaFund fund, List<DisclosureGap> gaps) implements FundPageDisclosures {}

  record NotChecked(TulevaFund fund, String reason) implements FundPageDisclosures {}
}
