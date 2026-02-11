package ee.tuleva.onboarding.savings.fund.issuing;

import java.math.BigDecimal;

record IssuingResult(BigDecimal cashAmount, BigDecimal fundUnits) {}
