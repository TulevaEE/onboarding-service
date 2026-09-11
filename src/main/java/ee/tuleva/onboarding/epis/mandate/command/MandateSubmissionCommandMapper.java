package ee.tuleva.onboarding.epis.mandate.command;

import ee.tuleva.onboarding.epis.mandate.GenericMandateDto;
import ee.tuleva.onboarding.mandate.GenericMandateSubmission;
import ee.tuleva.onboarding.mandate.MandateProcessResult;
import ee.tuleva.onboarding.mandate.MandateProcessResult.MandateProcessOutcome;
import ee.tuleva.onboarding.mandate.MandateSubmissionCommand;
import ee.tuleva.onboarding.mandate.details.MandateDetails;
import java.util.List;

class MandateSubmissionCommandMapper {

  private MandateSubmissionCommandMapper() {}

  static <T extends MandateDetails> MandateCommand<T> toMandateCommand(
      MandateSubmissionCommand<T> command) {
    return new MandateCommand<>(command.processId(), toGenericMandateDto(command.submission()));
  }

  private static <T extends MandateDetails> GenericMandateDto<T> toGenericMandateDto(
      GenericMandateSubmission<T> submission) {
    return GenericMandateDto.<T>builder()
        .id(submission.id())
        .createdDate(submission.createdDate())
        .address(submission.address())
        .email(submission.email())
        .phoneNumber(submission.phoneNumber())
        .details(submission.details())
        .build();
  }

  static MandateProcessResult toProcessResult(MandateCommandResponse response) {
    return MandateProcessResult.builder()
        .outcomes(
            List.of(
                new MandateProcessOutcome(
                    response.getProcessId(), response.isSuccessful(), response.getErrorCode())))
        .build();
  }
}
