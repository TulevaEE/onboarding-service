package ee.tuleva.onboarding.epis.transaction;

import java.math.BigDecimal;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class ExchangeTransactionDto {
  private String securityFrom;
  private String securityTo;
  private String fundManagerFrom;
  private String fundManagerTo;
  private String code;
  private String firstName;
  private String name;
  private BigDecimal percentage;
  private BigDecimal unitAmount;
}
