package ee.tuleva.onboarding.investment.report.publishing;

import java.util.List;
import java.util.Map;

record FundPagesBeforePublish(List<String> refusals, Map<FundReportMapping, String> linkedReports) {

  FundPagesBeforePublish {
    refusals = List.copyOf(refusals);
    linkedReports = Map.copyOf(linkedReports);
  }
}
