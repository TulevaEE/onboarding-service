package ee.tuleva.onboarding.investment.epis;

import ee.tuleva.onboarding.investment.report.ReportType;
import java.time.LocalDate;

public record EpisReportProcessed(Long reportId, ReportType reportType, LocalDate reportDate) {}
