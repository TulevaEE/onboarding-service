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
    private BigDecimal units;
    private String currency;
    private Integer pillar;
    private boolean activeContributions;
}
