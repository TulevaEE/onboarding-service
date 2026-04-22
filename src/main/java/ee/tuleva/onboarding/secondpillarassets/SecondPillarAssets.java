package ee.tuleva.onboarding.secondpillarassets;

import java.math.BigDecimal;

public record SecondPillarAssets(
    boolean pikFlag,
    BigDecimal balance,
    BigDecimal employeeWithheldPortion,
    BigDecimal socialTaxPortion,
    BigDecimal additionalParentalBenefit,
    BigDecimal interest,
    BigDecimal compensation,
    BigDecimal insurance,
    BigDecimal corrections,
    BigDecimal inheritance,
    BigDecimal withdrawals) {}
