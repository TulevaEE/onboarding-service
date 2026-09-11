package ee.tuleva.onboarding.epis.mandate;

import ee.tuleva.onboarding.country.Country;
import ee.tuleva.onboarding.mandate.LegacyMandateSubmission;
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
import org.jspecify.annotations.Nullable;

@Data
@Builder
public class MandateDto {
  @NotNull private final Long id;

  @Nullable private final String processId;

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

  public static MandateDto from(LegacyMandateSubmission submission) {
    return LegacyMandateSubmissionMapper.toMandateDto(submission);
  }

  @AllArgsConstructor
  @NoArgsConstructor
  @Getter
  @Setter
  public static class MandateFundsTransferExchangeDTO {
    @Nullable private String processId;
    @Nullable private BigDecimal amount;
    @Nullable private String sourceFundIsin;
    @Nullable private String targetFundIsin;
    @Nullable private String targetPik;
  }
}
