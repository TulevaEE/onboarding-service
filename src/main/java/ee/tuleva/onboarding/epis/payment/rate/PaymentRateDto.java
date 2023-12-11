package ee.tuleva.onboarding.epis.payment.rate;

import ee.tuleva.onboarding.user.address.Address;
import java.math.BigDecimal;
import java.time.Instant;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import org.springframework.lang.Nullable;

@Data
@Builder
public class PaymentRateDto {

  @NotNull private Long id;

  @NotNull private String processId;

  @NotNull private BigDecimal rate;

  @NotNull private Instant createdDate;

  @Valid @Nullable private Address address;

  private String email;

  private String phoneNumber;
}
