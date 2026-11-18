package ee.tuleva.onboarding.investment.transaction;

import static ee.tuleva.onboarding.investment.report.ReportType.R17_PEVA;
import static ee.tuleva.onboarding.investment.report.ReportType.R21_RAVA;
import static java.util.stream.Collectors.joining;

import ee.tuleva.onboarding.investment.report.ReportType;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import org.jspecify.annotations.Nullable;

sealed interface InputGateFailure {

  String slackLine();

  record StalePositionReport(@Nullable LocalDate latestReportDate, LocalDate requiredReportDate)
      implements InputGateFailure {

    @Override
    public String slackLine() {
      String found = latestReportDate == null ? "puudub" : "on " + latestReportDate;
      return "SEB positsiooniraport %s, vaja %s: küsi %s raport SEB-lt"
          .formatted(found, requiredReportDate, requiredReportDate);
    }
  }

  record MissingR45(LocalDate reportDate, @Nullable LocalDate latestReportDate)
      implements InputGateFailure {

    @Override
    public String slackLine() {
      String latest = latestReportDate == null ? "" : " (viimane %s)".formatted(latestReportDate);
      return "R45 %s puudub%s: lae R45 EPIS-ist".formatted(reportDate, latest);
    }
  }

  record MissingPevaRavaReports(
      List<ReportType> missingReports, LocalDate executionDate, LocalDate countedFrom)
      implements InputGateFailure {

    @Override
    public String slackLine() {
      String reports =
          missingReports.stream().map(MissingPevaRavaReports::reportName).collect(joining(" ja "));
      String verb = missingReports.size() == 1 ? "puudub" : "puuduvad";
      return "%s %s (PEVA/RAVA täitmine %s, vood arvesse alates %s): lae EPIS-ist"
          .formatted(reports, verb, executionDate, countedFrom);
    }

    private static String reportName(ReportType reportType) {
      if (reportType == R17_PEVA) {
        return "R17";
      }
      if (reportType == R21_RAVA) {
        return "R21";
      }
      throw new IllegalArgumentException("Not a PEVA/RAVA report type: reportType=" + reportType);
    }
  }

  record MissingR16(YearMonth paymentMonth, LocalDate paymentDeadline, LocalDate countedFrom)
      implements InputGateFailure {

    @Override
    public String slackLine() {
      return "R16 %s puudub (väljamaksed %s, arvesse alates %s): lae R16 EPIS-ist"
          .formatted(paymentMonth, paymentDeadline, countedFrom);
    }
  }
}
