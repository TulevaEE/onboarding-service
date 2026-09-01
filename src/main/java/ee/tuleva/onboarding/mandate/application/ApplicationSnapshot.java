package ee.tuleva.onboarding.mandate.application;

import ee.tuleva.onboarding.applicationtype.ApplicationType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@Data
public class ApplicationSnapshot {

  private Long id;
  private Instant date;
  private @Nullable String documentNumber;
  private ApplicationStatus status;
  private ApplicationType type;
  private @Nullable String sourceFundIsin;
  private List<FundTransfer> fundTransferExchanges;
  private @Nullable String bankAccount;
  private @Nullable BigDecimal paymentRate;
  private @Nullable FundPensionDetails fundPensionDetails;

  public record FundTransfer(
      @Nullable String targetFundIsin, @Nullable String targetPik, @Nullable BigDecimal amount) {}

  public record FundPensionDetails(int durationYears, int paymentsPerYear) {}
}
