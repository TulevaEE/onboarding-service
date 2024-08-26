package ee.tuleva.onboarding.epis.mandate;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import ee.tuleva.onboarding.epis.mandate.details.MandateDetails;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonDeserialize(using = GenericMandateCreationDtoDeserializer.class)
public class GenericMandateCreationDto<TDetails extends MandateDetails> {
  @NotNull private final TDetails details;
}
