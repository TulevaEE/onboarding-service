package ee.tuleva.onboarding.mandate.batch;

import ee.tuleva.onboarding.mandate.Mandate;
import ee.tuleva.onboarding.mandate.generic.MandateDto;
import jakarta.annotation.Nullable;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.stream.Collectors;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@Builder
@ToString
public class MandateBatchDto {

  @Nullable private Long id;

  @Valid @NotNull private List<MandateDto<?>> mandates;

  public static MandateBatchDto from(MandateBatch mandateBatch) {
    List<MandateDto<?>> mandateDtos =
        mandateBatch.getMandates().stream()
            .map(Mandate::getMandateDto)
            .collect(Collectors.toList());

    return MandateBatchDto.builder().id(mandateBatch.getId()).mandates(mandateDtos).build();
  }
}
