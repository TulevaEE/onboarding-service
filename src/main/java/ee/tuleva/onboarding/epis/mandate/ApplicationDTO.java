package ee.tuleva.onboarding.epis.mandate;

import ee.tuleva.onboarding.epis.mandate.MandateDto.MandateFundsTransferExchangeDTO;
import ee.tuleva.onboarding.mandate.application.ApplicationType;
import java.time.Instant;
import java.util.ArrayList;
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
  @Builder.Default private List<MandateFundsTransferExchangeDTO> fundTransferExchanges = new ArrayList<>();
  private ApplicationType type;
  private String bankAccount;

  public boolean isWithdrawal() {
    return type.equals(ApplicationType.WITHDRAWAL) || type.equals(ApplicationType.EARLY_WITHDRAWAL);
  }
}
