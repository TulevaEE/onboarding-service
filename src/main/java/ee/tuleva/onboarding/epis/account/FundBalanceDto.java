package ee.tuleva.onboarding.epis.account;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.math.BigDecimal;

@Getter
@Setter
@Builder
@ToString
public class FundBalanceDto {
    private String isin;
    private BigDecimal value;
    private String currency;
    private int pillar = 2;
    private boolean activeContributions = false;
}
