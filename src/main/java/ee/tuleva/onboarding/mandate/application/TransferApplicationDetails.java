package ee.tuleva.onboarding.mandate.application;

import ee.tuleva.onboarding.fund.response.FundDto;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
public class TransferApplicationDetails implements ApplicationDetails {
    private String currency;
    private Instant date;
    private BigDecimal amount;
    private FundDto sourceFund;
    private FundDto targetFund;
}
