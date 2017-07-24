package ee.tuleva.onboarding.account;


import ee.tuleva.onboarding.fund.Fund;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@Builder
public class FundBalance {

    private Fund fund;
    private BigDecimal value;
    private String currency;

    @Builder.Default
    private int pillar = 2;

    @Builder.Default
    private boolean activeContributions = false;

}
