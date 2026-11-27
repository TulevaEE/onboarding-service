package ee.tuleva.onboarding.investment.transaction;

import java.time.LocalDate;
import org.jspecify.annotations.NullMarked;

@NullMarked
record MissingIndexLevel(String isin, String indexKey, LocalDate date) {}
