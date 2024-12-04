package ee.tuleva.onboarding.epis.mandate;

import ee.tuleva.onboarding.epis.mandate.MandateDto.MandateFundsTransferExchangeDTO;
import ee.tuleva.onboarding.mandate.application.ApplicationType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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

  public record FundPensionDetails(int durationYears, int paymentsPerYear) {}
}
