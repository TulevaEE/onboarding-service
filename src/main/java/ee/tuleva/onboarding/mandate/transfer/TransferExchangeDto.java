package ee.tuleva.onboarding.mandate.transfer;

import ee.tuleva.onboarding.epis.mandate.MandateApplicationStatus;
import ee.tuleva.onboarding.fund.response.FundBalanceResponseDto;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

@Data
public class TransferExchangeDto {
    private String currency;
    private Instant date;
    private BigDecimal amount;
    private MandateApplicationStatus status;
    private FundBalanceResponseDto.FundResponseDto sourceFund;
    private FundBalanceResponseDto.FundResponseDto targetFund;

    public TransferExchangeDto(TransferExchange transferExchange, String language) {
        this.currency = transferExchange.getCurrency();
        this.date = transferExchange.getDate();
        this.amount = transferExchange.getAmount();
        this.status = transferExchange.getStatus();
        sourceFund = new FundBalanceResponseDto.FundResponseDto(transferExchange.getSourceFund(), language);
        targetFund = new FundBalanceResponseDto.FundResponseDto(transferExchange.getTargetFund(), language);
    }
}
