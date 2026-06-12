package ee.tuleva.onboarding.investment.transaction;

import ee.tuleva.onboarding.fund.TulevaFund;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.jspecify.annotations.NullMarked;

@NullMarked
public record FtConfirmation(
    @NotNull TulevaFund fund,
    @NotBlank String isin,
    @NotNull LocalDate tradeDate,
    @NotNull BigDecimal quantity,
    @NotNull BigDecimal grossPrice) {}
