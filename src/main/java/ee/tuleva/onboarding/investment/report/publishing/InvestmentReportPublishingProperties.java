package ee.tuleva.onboarding.investment.report.publishing;

import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "investment-report-publishing")
record InvestmentReportPublishingProperties(List<TulevaFund> funds) {

  InvestmentReportPublishingProperties {
    if (funds == null || funds.isEmpty()) {
      throw new IllegalStateException(
          "No funds configured to publish investment reports for:"
              + " property=investment-report-publishing.funds");
    }
    funds = List.copyOf(funds);
  }
}
