package ee.tuleva.onboarding.mandate.transfer;

import ee.tuleva.onboarding.fund.Fund;
import ee.tuleva.onboarding.epis.mandate.MandateApplicationStatus;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Builder
public class TransferExchange {

    private String currency;
    private Instant date;
    private BigDecimal amount;
    private MandateApplicationStatus status;
    private Fund sourceFund;
    private Fund targetFund;

}
