package ee.tuleva.onboarding.investment.event;

import ee.tuleva.onboarding.investment.report.ReportProvider;
import ee.tuleva.onboarding.investment.report.ReportType;
import java.time.LocalDate;

public record ReportImportCompleted(
    ReportProvider provider, ReportType reportType, LocalDate reportDate, int importedRowCount) {}
