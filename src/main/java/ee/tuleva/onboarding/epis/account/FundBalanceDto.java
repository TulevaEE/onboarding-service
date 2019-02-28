package ee.tuleva.onboarding.epis.account;

import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@ToString
public class FundBalanceDto {
    private String isin;
    private BigDecimal value;
    private String currency;
    private int pillar = 2;
    private boolean activeContributions = false;
}
