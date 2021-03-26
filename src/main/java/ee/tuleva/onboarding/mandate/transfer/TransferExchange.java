package ee.tuleva.onboarding.mandate.transfer;

import ee.tuleva.onboarding.epis.mandate.ApplicationStatus;
import ee.tuleva.onboarding.fund.Fund;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Deprecated
public class TransferExchange {

  private String currency;
  private Instant date;
  private BigDecimal amount;
  private ApplicationStatus status;
  private Fund sourceFund;
  private Fund targetFund;

  public Integer getPillar() {
    if (isTransferCancellation() || sourceFund.getPillar().equals(targetFund.getPillar())) {
      return sourceFund.getPillar();
    }
    throw new IllegalStateException("Transfer between different pillar funds");
  }

  private boolean isTransferCancellation() {
    return targetFund == null;
  }
}
