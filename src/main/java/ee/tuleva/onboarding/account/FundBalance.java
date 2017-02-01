package ee.tuleva.onboarding.account;


import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@Builder
public class FundBalance {
    private String isin;
    private String name;
    private String manager;
    private BigDecimal price;
    private String currency;
    private int pillar = 2;
}
