package ee.tuleva.onboarding.investment.epis;

import ee.tuleva.onboarding.fund.TulevaFund;
import java.math.BigDecimal;
import java.time.LocalDate;

public record R16FundFlow(
    TulevaFund fund,
    BigDecimal fondimaksedUnits,
    BigDecimal uhekordsedUnits,
    BigDecimal totalOutflowEur,
    LocalDate paymentMonth,
    LocalDate paymentDeadline,
    LocalDate sellByDate) {}
