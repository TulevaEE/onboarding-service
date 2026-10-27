package ee.tuleva.onboarding.investment.transaction.ingest;

import java.time.LocalDate;
import java.util.List;

record SebFundConfirmationMissingScan(
    List<MissingSebFundConfirmation> missing, List<LocalDate> incompleteReportDates) {}
