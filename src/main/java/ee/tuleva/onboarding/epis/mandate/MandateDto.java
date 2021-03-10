package ee.tuleva.onboarding.epis.mandate;

import ee.tuleva.onboarding.user.address.Address;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import javax.validation.constraints.NotNull;
import lombok.*;
import org.jetbrains.annotations.Nullable;

@Data
@Builder
public class MandateDto {
  @NotNull private final Long id;

  @NotNull private final String processId;

  @Nullable private final String futureContributionFundIsin;

  @NotNull private Instant createdDate;

  @NotNull private Integer pillar;

  private List<MandateFundsTransferExchangeDTO> fundTransferExchanges;

  @Nullable private Address address;

  @AllArgsConstructor
  @Getter
  @Setter
  public static class MandateFundsTransferExchangeDTO {
    private String processId;
    private BigDecimal amount;
    private String sourceFundIsin;
    private String targetFundIsin;
  }
}
