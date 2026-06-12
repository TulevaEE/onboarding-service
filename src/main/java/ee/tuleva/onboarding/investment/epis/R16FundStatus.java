package ee.tuleva.onboarding.investment.epis;

import ee.tuleva.onboarding.fund.TulevaFund;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

public record R16FundStatus(
    TulevaFund fund,
    R16Phase phase,
    @Nullable BigDecimal fondimaksedUnits,
    @Nullable BigDecimal uhekordsedUnits,
    @Nullable BigDecimal totalOutflowEur,
    @Nullable LocalDate paymentMonth,
    @Nullable LocalDate paymentDeadline,
    @Nullable LocalDate sellByDate,
    boolean suppressedByR45) {}
