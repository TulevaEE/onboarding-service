package ee.tuleva.onboarding.investment.fees.instrument;

import java.time.LocalDate;
import java.util.List;
import java.util.SortedSet;

record AccrualRun(
    List<LocalDate> accruedDays,
    List<LocalDate> failedDays,
    SortedSet<String> isinsWithoutAProviderAgreement) {}
