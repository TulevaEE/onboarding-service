package ee.tuleva.onboarding.mandate.application;

import ee.tuleva.onboarding.fund.response.FundDto;
import java.math.BigDecimal;
import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.Singular;

@Data
@Builder
public class TransferApplicationDetails implements ApplicationDetails {

  private FundDto sourceFund;
  @Singular private List<Exchange> exchanges;

  @Data
  @Builder
  public static class Exchange {

    private FundDto targetFund;
    private BigDecimal amount;
  }
}
