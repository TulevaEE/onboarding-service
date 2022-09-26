package ee.tuleva.onboarding.epis.mandate;

import ee.tuleva.onboarding.user.address.Address;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
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

  @Nullable private Address address;

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
