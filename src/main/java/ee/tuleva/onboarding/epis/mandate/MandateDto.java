package ee.tuleva.onboarding.epis.mandate;

import ee.tuleva.onboarding.country.Country;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.jetbrains.annotations.Nullable;

@Data
@Builder
public class MandateDto {
  @NotNull private final Long id;

  @NotNull private final String processId;

  @Nullable private final String futureContributionFundIsin;

  @NotNull private Instant createdDate;

  @NotNull
  @Min(2)
  @Max(3)
  private Integer pillar;

  private List<MandateFundsTransferExchangeDTO> fundTransferExchanges;

  @Nullable private Country address;

  private String email;

  private String phoneNumber;

  private Optional<BigDecimal> paymentRate;

  @AllArgsConstructor
  @NoArgsConstructor
  @Getter
  @Setter
  public static class MandateFundsTransferExchangeDTO {
    private String processId;
    private BigDecimal amount;
    private String sourceFundIsin;
    private String targetFundIsin;
    private String targetPik;
  }
}
