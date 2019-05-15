package ee.tuleva.onboarding.mandate.transfer;

import ee.tuleva.onboarding.epis.mandate.MandateApplicationStatus;
import ee.tuleva.onboarding.fund.response.FundDto;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

@Data
class TransferExchangeDto {
    private String currency;
    private Instant date;
    private BigDecimal amount;
    private MandateApplicationStatus status;
    private FundDto sourceFund;
    private FundDto targetFund;

    TransferExchangeDto(TransferExchange transferExchange, String language) {
        this.currency = transferExchange.getCurrency();
        this.date = transferExchange.getDate();
        this.amount = transferExchange.getAmount();
        this.status = transferExchange.getStatus();
        sourceFund = new FundDto(transferExchange.getSourceFund(), language);
        targetFund = new FundDto(transferExchange.getTargetFund(), language);
    }
}
