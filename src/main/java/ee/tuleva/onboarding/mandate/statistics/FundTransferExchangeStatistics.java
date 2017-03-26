package ee.tuleva.onboarding.mandate.statistics;

import ee.tuleva.onboarding.mandate.FundTransferExchange;
import lombok.Builder;
import lombok.Data;
import org.hibernate.validator.constraints.NotBlank;

import javax.persistence.*;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;

@Entity
@Table(name = "fund_transfer_exchange_statistics")
@Data
@Builder
public class FundTransferExchangeStatistics {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @NotBlank
    private String sourceFundIsin;
    @NotNull
    private String targetFundIsin;
    @NotNull
    @Min(0)
    @Max(1)
    private BigDecimal amount;
    @NotNull
    @Min(0)
    private BigDecimal value;

    public FundTransferExchangeStatistics fromFundTransferExchange(FundTransferExchange fundTransferExchange) {
        FundTransferExchangeStatistics.builder()
                .sourceFundIsin(fundTransferExchange.getSourceFundIsin())
                .targetFundIsin(fundTransferExchange.getTargetFundIsin())
                .



    }

}
