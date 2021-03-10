package ee.tuleva.onboarding.mandate.application;

import java.math.BigDecimal;
import java.time.Instant;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TransferApplicationDetails implements ApplicationDetails {
  private String currency;
  private Instant date;
  private BigDecimal amount;
  private String sourceFundIsin;
  private String targetFundIsin;
}
