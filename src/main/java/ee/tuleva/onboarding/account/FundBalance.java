package ee.tuleva.onboarding.account;


import ee.tuleva.domain.fund.Fund;
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
    private int pillar = 2;
    private boolean activeFund = false;
    /*
    @Deprecated
    private BigDecimal price; // need to be changes to 'value'
    @Deprecated
    private BigDecimal managementFeeRate;
    @Deprecated
    private String isin;
    @Deprecated
    private String name;
    @Deprecated
    private String manager;
    */
}
