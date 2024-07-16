package ee.tuleva.onboarding.epis.mandate;

import ee.tuleva.onboarding.epis.mandate.details.MandateDetails;
import ee.tuleva.onboarding.mandate.application.ApplicationType;
import ee.tuleva.onboarding.user.address.Address;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import lombok.Builder;
import lombok.Data;
import org.jetbrains.annotations.Nullable;

@Data
@Builder
public class GenericMandateDto<TDetails extends MandateDetails> {
  @NotNull private final Long id;

  @NotNull private final TDetails details;

  @NotNull private Instant createdDate;

  @Nullable private Address address;

  private String email;

  private String phoneNumber;

  public ApplicationType getType() {
    return details.getType();
  }
}
