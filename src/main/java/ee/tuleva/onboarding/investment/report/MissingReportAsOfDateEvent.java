package ee.tuleva.onboarding.investment.report;

import java.time.LocalDate;

public record MissingReportAsOfDateEvent(
    ReportProvider provider, ReportType reportType, LocalDate reportDate) {}
