package ee.tuleva.onboarding.mandate.batch;

import static ee.tuleva.onboarding.mandate.MandateType.FUND_PENSION_OPENING;
import static ee.tuleva.onboarding.mandate.MandateType.PARTIAL_WITHDRAWAL;
import static ee.tuleva.onboarding.pillar.Pillar.THIRD;
import static java.util.stream.Collectors.toSet;

import ee.tuleva.onboarding.epis.mandate.details.FundPensionOpeningMandateDetails;
import ee.tuleva.onboarding.epis.mandate.details.PartialWithdrawalMandateDetails;
import ee.tuleva.onboarding.mandate.Mandate;
import ee.tuleva.onboarding.mandate.generic.MandateDto;
import ee.tuleva.onboarding.pillar.Pillar;
import jakarta.annotation.Nullable;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
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

  public boolean isWithdrawalBatch() {
    return mandates.stream().anyMatch(mandateDto -> mandateDto.getMandateType().isWithdrawalType());
  }

  public Set<Pillar> getWithdrawalBatchPillars() {
    var fundPensionOpeningMandatePillars =
        mandates.stream()
            .filter(mandate -> mandate.getMandateType() == FUND_PENSION_OPENING)
            .map(mandate -> ((FundPensionOpeningMandateDetails) mandate.getDetails()).getPillar())
            .collect(toSet());

    var partialWithdrawalMandatePillars =
        mandates.stream()
            .filter(mandate -> mandate.getMandateType() == PARTIAL_WITHDRAWAL)
            .map(mandate -> ((PartialWithdrawalMandateDetails) mandate.getDetails()).getPillar())
            .collect(toSet());

    return Stream.concat(
            fundPensionOpeningMandatePillars.stream(), partialWithdrawalMandatePillars.stream())
        .collect(toSet());
  }

  public boolean isBatchOnlyThirdPillarPartialWithdrawal() {
    var mandates = getMandates();

    if (mandates.size() > 1) {
      return false;
    }

    var mandateDto = mandates.getFirst();

    if (mandateDto.getMandateType() != PARTIAL_WITHDRAWAL) {
      return false;
    }

    var details = (PartialWithdrawalMandateDetails) mandateDto.getDetails();

    return details.getPillar() == THIRD;
  }
}
