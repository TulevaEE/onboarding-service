package ee.tuleva.onboarding.epis.mandate;

import com.fasterxml.jackson.annotation.JsonIgnore;
import ee.tuleva.onboarding.country.Country;
import ee.tuleva.onboarding.mandate.MandateType;
import ee.tuleva.onboarding.mandate.details.MandateDetails;
import java.time.Instant;
import lombok.Builder;
import lombok.Data;
import org.jspecify.annotations.Nullable;

@Data
@Builder
public class GenericMandateDto<TDetails extends MandateDetails> {
  private final @Nullable Long id;

  private final TDetails details;

  private @Nullable Instant createdDate;

  @Nullable private Country address;

  private String email;

  private String phoneNumber;

  @JsonIgnore
  public MandateType getMandateType() {
    return details.getMandateType();
  }
}
