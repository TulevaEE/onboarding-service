package ee.tuleva.onboarding.epis.mandate;

import com.fasterxml.jackson.annotation.JsonIgnore;
import ee.tuleva.onboarding.country.Country;
import ee.tuleva.onboarding.epis.mandate.details.MandateDetails;
import ee.tuleva.onboarding.mandate.MandateType;
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

  @Nullable private Country address;

  private String email;

  private String phoneNumber;

  @JsonIgnore
  public MandateType getMandateType() {
    return details.getMandateType();
  }
}
