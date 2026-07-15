package ee.tuleva.onboarding.investment.transaction.ingest;

import java.time.LocalDate;

record PossibleReportTruncationEvent(
    LocalDate reportDate, int rowCount, LocalDate priorReportDate, int priorRowCount) {}
