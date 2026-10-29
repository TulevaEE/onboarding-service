package ee.tuleva.onboarding.investment.transaction.ingest;

import java.time.LocalDate;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("transaction-registry.seb-fund-confirmation")
record SebFundConfirmationProperties(
    int maxNavDatesAfterReceipt, int businessDaysToWait, LocalDate missingConfirmationsFrom) {}
