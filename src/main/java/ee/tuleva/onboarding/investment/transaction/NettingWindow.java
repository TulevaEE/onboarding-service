package ee.tuleva.onboarding.investment.transaction;

import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.NullMarked;

@NullMarked
record NettingWindow(
    TulevaFund fund, LocalDate reportDate, LocalDate today, Set<UUID> pendingEntryReferences) {}
