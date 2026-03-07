package ee.tuleva.onboarding.investment.fees;

import java.math.BigDecimal;

public record FeeResult(BigDecimal managementFeeAccrual, BigDecimal depotFeeAccrual) {}
