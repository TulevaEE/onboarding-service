package ee.tuleva.onboarding.income;


import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@Builder
public class Money {
    private BigDecimal amount;
    private String currency;
}
