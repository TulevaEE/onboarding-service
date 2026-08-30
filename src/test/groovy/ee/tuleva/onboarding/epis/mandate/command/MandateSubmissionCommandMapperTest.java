package ee.tuleva.onboarding.epis.mandate.command;

import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.country.Country;
import ee.tuleva.onboarding.epis.mandate.GenericMandateDto;
import ee.tuleva.onboarding.mandate.GenericMandateSubmission;
import ee.tuleva.onboarding.mandate.MandateProcessResult;
import ee.tuleva.onboarding.mandate.MandateProcessResult.MandateProcessOutcome;
import ee.tuleva.onboarding.mandate.MandateSubmissionCommand;
import ee.tuleva.onboarding.mandate.details.EarlyWithdrawalCancellationMandateDetails;
import ee.tuleva.onboarding.mandate.details.SelectionMandateDetails;
import ee.tuleva.onboarding.mandate.details.WithdrawalCancellationMandateDetails;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class MandateSubmissionCommandMapperTest {

  private static final Instant DATE = Instant.parse("2021-03-09T10:00:00Z");

  @Test
  void mapsSubmissionWithSelectionDetails() {
    var submission =
        GenericMandateSubmission.<SelectionMandateDetails>builder()
            .id(123L)
            .createdDate(DATE)
            .address(new Country("EE"))
            .email("test@tuleva.ee")
            .phoneNumber("+37288888888")
            .details(new SelectionMandateDetails("EE3600109435"))
            .build();
    var command = new MandateSubmissionCommand<>("processId1", submission);

    GenericMandateDto<SelectionMandateDetails> expected =
        GenericMandateDto.<SelectionMandateDetails>builder()
            .id(123L)
            .createdDate(DATE)
            .address(new Country("EE"))
            .email("test@tuleva.ee")
            .phoneNumber("+37288888888")
            .details(new SelectionMandateDetails("EE3600109435"))
            .build();

    MandateCommand<SelectionMandateDetails> result =
        MandateSubmissionCommandMapper.toMandateCommand(command);

    assertThat(result.getMandateDto()).usingRecursiveComparison().isEqualTo(expected);
    assertThat(result.getProcessId()).isEqualTo("processId1");
  }

  @Test
  void mapsSubmissionWithWithdrawalCancellationDetails() {
    var submission =
        GenericMandateSubmission.<WithdrawalCancellationMandateDetails>builder()
            .id(875L)
            .createdDate(DATE)
            .address(new Country("EE"))
            .email("email@override.ee")
            .phoneNumber("+37288888888")
            .details(new WithdrawalCancellationMandateDetails())
            .build();
    var command = new MandateSubmissionCommand<>("processId2", submission);

    GenericMandateDto<WithdrawalCancellationMandateDetails> expected =
        GenericMandateDto.<WithdrawalCancellationMandateDetails>builder()
            .id(875L)
            .createdDate(DATE)
            .address(new Country("EE"))
            .email("email@override.ee")
            .phoneNumber("+37288888888")
            .details(new WithdrawalCancellationMandateDetails())
            .build();

    MandateCommand<WithdrawalCancellationMandateDetails> result =
        MandateSubmissionCommandMapper.toMandateCommand(command);

    assertThat(result.getMandateDto()).usingRecursiveComparison().isEqualTo(expected);
    assertThat(result.getProcessId()).isEqualTo("processId2");
  }

  @Test
  void mapsSubmissionWithoutAddress() {
    var submission =
        GenericMandateSubmission.<EarlyWithdrawalCancellationMandateDetails>builder()
            .id(321L)
            .createdDate(DATE)
            .email("email@override.ee")
            .phoneNumber("+37288888888")
            .details(new EarlyWithdrawalCancellationMandateDetails())
            .build();
    var command = new MandateSubmissionCommand<>("processId3", submission);

    GenericMandateDto<EarlyWithdrawalCancellationMandateDetails> expected =
        GenericMandateDto.<EarlyWithdrawalCancellationMandateDetails>builder()
            .id(321L)
            .createdDate(DATE)
            .email("email@override.ee")
            .phoneNumber("+37288888888")
            .details(new EarlyWithdrawalCancellationMandateDetails())
            .build();

    assertThat(MandateSubmissionCommandMapper.toMandateCommand(command).getMandateDto())
        .usingRecursiveComparison()
        .isEqualTo(expected);
  }

  @Test
  void mapsSuccessfulResponseToProcessResult() {
    MandateCommandResponse response = new MandateCommandResponse("processId1", true, null, null);

    MandateProcessResult expected =
        MandateProcessResult.builder()
            .outcomes(List.of(new MandateProcessOutcome("processId1", true, null)))
            .build();

    assertThat(MandateSubmissionCommandMapper.toProcessResult(response))
        .usingRecursiveComparison()
        .isEqualTo(expected);
    assertThat(response.toProcessResult()).usingRecursiveComparison().isEqualTo(expected);
  }

  @Test
  void mapsErrorResponseToProcessResult() {
    MandateCommandResponse response =
        new MandateCommandResponse("processId2", false, 7, "mandate rejected");

    MandateProcessResult expected =
        MandateProcessResult.builder()
            .outcomes(List.of(new MandateProcessOutcome("processId2", false, 7)))
            .build();

    assertThat(MandateSubmissionCommandMapper.toProcessResult(response))
        .usingRecursiveComparison()
        .isEqualTo(expected);
  }
}
