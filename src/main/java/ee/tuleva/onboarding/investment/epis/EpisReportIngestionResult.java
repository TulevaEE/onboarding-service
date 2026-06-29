package ee.tuleva.onboarding.investment.epis;

import ee.tuleva.onboarding.fund.TulevaFund;
import ee.tuleva.onboarding.investment.report.ReportType;
import java.time.LocalDate;
import java.util.Map;

public record EpisReportIngestionResult(
    Long reportId,
    ReportType reportType,
    LocalDate reportDate,
    Map<TulevaFund, Map<String, Object>> fundSummaries) {}
