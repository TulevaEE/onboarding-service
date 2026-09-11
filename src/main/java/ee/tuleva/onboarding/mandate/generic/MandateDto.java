package ee.tuleva.onboarding.mandate.generic;

import com.fasterxml.jackson.annotation.JsonIgnore;
import ee.tuleva.onboarding.mandate.MandateType;
import ee.tuleva.onboarding.mandate.details.MandateDetails;
import java.time.Instant;
import lombok.Builder;
import lombok.Data;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.annotation.JsonDeserialize;

@Data
@Builder
@JsonDeserialize(using = MandateDtoDeserializer.class)
public class MandateDto<TDetails extends MandateDetails> {
  @Nullable private final Long id;

  private final TDetails details;

  @Nullable private Instant createdDate;

  @JsonIgnore
  public MandateType getMandateType() {
    return details.getMandateType();
  }
}
