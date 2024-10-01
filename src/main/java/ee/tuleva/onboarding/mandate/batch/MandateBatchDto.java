package ee.tuleva.onboarding.mandate.batch;

import ee.tuleva.onboarding.mandate.Mandate;
import ee.tuleva.onboarding.mandate.generic.MandateDto;
import jakarta.annotation.Nullable;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.stream.Collectors;
import lombok.*;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@ToString
public class MandateBatchDto {

  @Valid @NotNull private List<MandateDto<?>> mandates;
  @Nullable private Long id;

  public static MandateBatchDto from(MandateBatch mandateBatch) {
    List<MandateDto<?>> mandateDtos =
        mandateBatch.getMandates().stream()
            .map(Mandate::getMandateDto)
            .collect(Collectors.toList());

    return MandateBatchDto.builder().id(mandateBatch.getId()).mandates(mandateDtos).build();
  }
}
