package ee.tuleva.onboarding.investment.report.publishing;

import ee.tuleva.onboarding.investment.report.publishing.ReportPublication.NotChecked;
import ee.tuleva.onboarding.investment.report.publishing.ReportPublication.NotLinked;
import ee.tuleva.onboarding.investment.report.publishing.wordpress.WordPressPageReader;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.time.YearMonth;
import java.util.List;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
class InvestmentReportPublicationCheck {

  private static final int MAX_REASON_LENGTH_IN_SLACK = 200;

  private final WordPressPageReader pageReader;

  List<ReportPublication> check(YearMonth month) {
    return pensionFundReports().stream().map(report -> publicationOf(report, month)).toList();
  }

  private static List<FundReportMapping> pensionFundReports() {
    return Stream.of(TulevaFund.getPillar2Funds(), TulevaFund.getPillar3Funds())
        .flatMap(List::stream)
        .map(FundReportMapping::forFund)
        .toList();
  }

  private ReportPublication publicationOf(FundReportMapping report, YearMonth month) {
    var fund = report.fund();
    try {
      return pageReader
          .investmentReportUrl(report.pageSlug())
          .map(reportUrl -> ReportPublication.linking(fund, reportUrl, month))
          .orElseGet(() -> new NotLinked(fund));
    } catch (Exception e) {
      log.error(
          "Investment report publication check failed: fund={}, pageSlug={}, month={}",
          fund.getCode(),
          report.pageSlug(),
          month,
          e);
      return new NotChecked(fund, describe(e));
    }
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
