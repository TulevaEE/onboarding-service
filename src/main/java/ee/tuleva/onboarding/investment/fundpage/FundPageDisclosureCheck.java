package ee.tuleva.onboarding.investment.fundpage;

import ee.tuleva.onboarding.investment.fundpage.FundPageDisclosures.NotChecked;
import ee.tuleva.onboarding.investment.report.publishing.FundReportMapping;
import ee.tuleva.onboarding.investment.report.publishing.wordpress.DisclosureGap;
import ee.tuleva.onboarding.investment.report.publishing.wordpress.WordPressPageReader;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
class FundPageDisclosureCheck {

  private static final int MAX_REASON_LENGTH_IN_SLACK = 200;

  private final WordPressPageReader pageReader;

  List<FundPageDisclosures> check() {
    return FundReportMapping.all().stream().map(this::disclosuresOn).toList();
  }

  private FundPageDisclosures disclosuresOn(FundReportMapping fundPage) {
    var fund = fundPage.fund();
    try {
      return FundPageDisclosures.listing(
          fund, notCheckedElsewhere(fund, pageReader.disclosureGaps(fundPage.pageSlug())));
    } catch (Exception e) {
      log.error(
          "Fund page disclosure check failed: fund={}, pageSlug={}",
          fund.getCode(),
          fundPage.pageSlug(),
          e);
      return new NotChecked(fund, describe(e));
    }
  }

  private static List<DisclosureGap> notCheckedElsewhere(
      TulevaFund fund, List<DisclosureGap> gaps) {
    return gaps.stream()
        .filter(
            gap ->
                !(gap.concernsTheInvestmentReport()
                    && hasItsReportCheckedByTheInvestmentReportPublicationCheck(fund)))
        .toList();
  }

  private static boolean hasItsReportCheckedByTheInvestmentReportPublicationCheck(TulevaFund fund) {
    return TulevaFund.getPillar2Funds().contains(fund)
        || TulevaFund.getPillar3Funds().contains(fund);
  }

  private static String describe(Exception e) {
    var message = e.getMessage();
    var description =
        message == null
            ? e.getClass().getSimpleName()
            : e.getClass().getSimpleName() + ": " + message;
    return description.length() <= MAX_REASON_LENGTH_IN_SLACK
        ? description
        : description.substring(0, MAX_REASON_LENGTH_IN_SLACK) + "…";
  }
}
