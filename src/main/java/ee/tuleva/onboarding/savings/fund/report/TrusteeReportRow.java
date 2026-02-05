package ee.tuleva.onboarding.savings.fund.report;

import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Builder;

@Builder
record TrusteeReportRow(
    LocalDate reportDate,
    BigDecimal nav,
    BigDecimal issuedUnits,
    BigDecimal issuedAmount,
    BigDecimal redeemedUnits,
    BigDecimal redeemedAmount,
    BigDecimal totalOutstandingUnits) {}
