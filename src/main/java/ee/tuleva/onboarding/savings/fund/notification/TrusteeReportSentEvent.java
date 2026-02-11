package ee.tuleva.onboarding.savings.fund.notification;

import java.math.BigDecimal;
import java.time.LocalDate;

public record TrusteeReportSentEvent(
    LocalDate reportDate,
    int rowCount,
    BigDecimal nav,
    BigDecimal issuedUnits,
    BigDecimal issuedAmount,
    BigDecimal redeemedUnits,
    BigDecimal redeemedAmount,
    BigDecimal totalOutstandingUnits) {}
