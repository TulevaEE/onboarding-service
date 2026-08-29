package ee.tuleva.onboarding.epis.mandate;

import static java.util.Objects.requireNonNull;

import ee.tuleva.onboarding.epis.mandate.MandateDto.MandateFundsTransferExchangeDTO;
import ee.tuleva.onboarding.mandate.ApplicationType;
import ee.tuleva.onboarding.mandate.application.ApplicationSnapshot;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
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
public class ApplicationDTO {

  private String currency;
  private Instant date;
  private Long id;
  private String documentNumber;
  private ApplicationStatus status;
  private String sourceFundIsin;
  private List<MandateFundsTransferExchangeDTO> fundTransferExchanges;
  private ApplicationType type;
  private String bankAccount;
  private BigDecimal paymentRate;
  private FundPensionDetails fundPensionDetails;

  public boolean isWithdrawal() {
    return type != null && type.isWithdrawal();
  }

  public boolean isPaymentRate() {
    return type != null && type.isPaymentRate();
  }

  public ApplicationSnapshot toSnapshot() {
    return ApplicationSnapshotMapper.toSnapshot(this);
  }

  public static List<ApplicationSnapshot> toSnapshots(ApplicationDTO @Nullable [] dtos) {
    return Arrays.stream(requireNonNull(dtos, "Applications response body missing"))
        .map(ApplicationDTO::toSnapshot)
        .toList();
  }

  public record FundPensionDetails(int durationYears, int paymentsPerYear) {}
}
