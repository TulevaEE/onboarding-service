package ee.tuleva.onboarding.epis.mandate;

import static java.util.Objects.requireNonNull;

import ee.tuleva.onboarding.epis.application.ApplicationResponse;
import ee.tuleva.onboarding.epis.mandate.MandateDto.MandateFundsTransferExchangeDTO;
import ee.tuleva.onboarding.mandate.LegacyMandateSubmission;
import ee.tuleva.onboarding.mandate.LegacyMandateSubmission.FundTransferInstruction;
import ee.tuleva.onboarding.mandate.MandateProcessResult;
import ee.tuleva.onboarding.mandate.MandateProcessResult.MandateProcessOutcome;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

class LegacyMandateSubmissionMapper {

  private LegacyMandateSubmissionMapper() {}

  static MandateDto toMandateDto(LegacyMandateSubmission submission) {
    return MandateDto.builder()
        .id(submission.id())
        .processId(submission.processId())
        .futureContributionFundIsin(submission.futureContributionFundIsin())
        .createdDate(submission.createdDate())
        .pillar(submission.pillar())
        .fundTransferExchanges(toFundTransferExchangeDtos(submission.fundTransferExchanges()))
        .address(submission.address())
        .email(submission.email())
        .phoneNumber(submission.phoneNumber())
        .paymentRate(Optional.ofNullable(submission.paymentRate()))
        .build();
  }

  private static List<MandateFundsTransferExchangeDTO> toFundTransferExchangeDtos(
      List<FundTransferInstruction> instructions) {
    return instructions.stream()
        .map(LegacyMandateSubmissionMapper::toFundTransferExchangeDto)
        .toList();
  }

  private static MandateFundsTransferExchangeDTO toFundTransferExchangeDto(
      FundTransferInstruction instruction) {
    return new MandateFundsTransferExchangeDTO(
        instruction.processId(),
        instruction.amount(),
        instruction.sourceFundIsin(),
        instruction.targetFundIsin(),
        instruction.targetPik());
  }

  static MandateProcessResult toProcessResult(
      @Nullable List<ApplicationResponse> mandateResponses) {
    List<ApplicationResponse> responses =
        requireNonNull(mandateResponses, "Missing mandate responses in EPIS response");
    return MandateProcessResult.builder()
        .outcomes(responses.stream().map(LegacyMandateSubmissionMapper::toOutcome).toList())
        .build();
  }

  private static MandateProcessOutcome toOutcome(ApplicationResponse response) {
    return new MandateProcessOutcome(
        response.getProcessId(), response.isSuccessful(), response.getErrorCode());
  }
}
