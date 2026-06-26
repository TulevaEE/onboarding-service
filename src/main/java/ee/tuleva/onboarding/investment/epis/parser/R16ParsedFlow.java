package ee.tuleva.onboarding.investment.epis.parser;

import ee.tuleva.onboarding.fund.TulevaFund;
import java.math.BigDecimal;
import java.time.YearMonth;

public record R16ParsedFlow(
    TulevaFund fund,
    BigDecimal fondimaksedUnits,
    BigDecimal uhekordsedUnits,
    YearMonth paymentMonth) {}
