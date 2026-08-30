package ee.tuleva.onboarding.epis.mandate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ee.tuleva.onboarding.country.Country;
import ee.tuleva.onboarding.epis.application.ApplicationResponse;
import ee.tuleva.onboarding.epis.mandate.MandateDto.MandateFundsTransferExchangeDTO;
import ee.tuleva.onboarding.mandate.ApplicationType;
import ee.tuleva.onboarding.mandate.LegacyMandateSubmission;
import ee.tuleva.onboarding.mandate.LegacyMandateSubmission.FundTransferInstruction;
import ee.tuleva.onboarding.mandate.MandateProcessResult;
import ee.tuleva.onboarding.mandate.MandateProcessResult.MandateProcessOutcome;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class LegacyMandateSubmissionMapperTest {

  private static final Instant DATE = Instant.parse("2021-03-09T10:00:00Z");

  @Test
  void mapsSubmissionWithBothFundTransferShapes() {
    LegacyMandateSubmission submission =
        LegacyMandateSubmission.builder()
            .id(123L)
            .processId("processId1")
            .futureContributionFundIsin("EE3600109435")
            .createdDate(DATE)
            .pillar(2)
            .fundTransferExchanges(
                List.of(
                    new FundTransferInstruction(
                        "processId2", BigDecimal.valueOf(1.5), "EE1", "EE2", null),
                    new FundTransferInstruction(
                        "processId3", null, "EE3", null, "EE471000001020145685")))
            .address(new Country("EE"))
            .email("test@tuleva.ee")
            .phoneNumber("+37288888888")
            .paymentRate(BigDecimal.valueOf(4))
            .build();

    MandateDto expected =
        MandateDto.builder()
            .id(123L)
            .processId("processId1")
            .futureContributionFundIsin("EE3600109435")
            .createdDate(DATE)
            .pillar(2)
            .fundTransferExchanges(
                List.of(
                    new MandateFundsTransferExchangeDTO(
                        "processId2", BigDecimal.valueOf(1.5), "EE1", "EE2", null),
                    new MandateFundsTransferExchangeDTO(
                        "processId3", null, "EE3", null, "EE471000001020145685")))
            .address(new Country("EE"))
            .email("test@tuleva.ee")
            .phoneNumber("+37288888888")
            .paymentRate(Optional.of(BigDecimal.valueOf(4)))
            .build();

    assertThat(LegacyMandateSubmissionMapper.toMandateDto(submission))
        .usingRecursiveComparison()
        .isEqualTo(expected);
  }

  @Test
  void mapsSubmissionWithoutOptionalFields() {
    LegacyMandateSubmission submission =
        LegacyMandateSubmission.builder()
            .id(124L)
            .createdDate(DATE)
            .pillar(3)
            .fundTransferExchanges(List.of())
            .email("test@tuleva.ee")
            .phoneNumber("+37288888888")
            .build();

    MandateDto expected =
        MandateDto.builder()
            .id(124L)
            .createdDate(DATE)
            .pillar(3)
            .fundTransferExchanges(List.of())
            .email("test@tuleva.ee")
            .phoneNumber("+37288888888")
            .paymentRate(Optional.empty())
            .build();

    assertThat(LegacyMandateSubmissionMapper.toMandateDto(submission))
        .usingRecursiveComparison()
        .isEqualTo(expected);
  }

  @Test
  void mapsSuccessfulAndFailedOutcomes() {
    List<ApplicationResponse> mandateResponses =
        List.of(
            ApplicationResponse.builder()
                .processId("processId2")
                .applicationType(ApplicationType.TRANSFER)
                .successful(true)
                .build(),
            ApplicationResponse.builder()
                .processId("processId3")
                .applicationType(ApplicationType.WITHDRAWAL)
                .successful(false)
                .errorCode(7)
                .errorMessage("failure")
                .build());

    MandateProcessResult expected =
        MandateProcessResult.builder()
            .outcomes(
                List.of(
                    new MandateProcessOutcome("processId2", true, null),
                    new MandateProcessOutcome("processId3", false, 7)))
            .build();

    assertThat(LegacyMandateSubmissionMapper.toProcessResult(mandateResponses))
        .usingRecursiveComparison()
        .isEqualTo(expected);

    ApplicationResponseDTO response = new ApplicationResponseDTO();
    response.setMandateResponses(mandateResponses);
    assertThat(response.toProcessResult()).usingRecursiveComparison().isEqualTo(expected);
  }

  @Test
  void throwsWhenMandateResponsesMissing() {
    assertThatThrownBy(() -> LegacyMandateSubmissionMapper.toProcessResult(null))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> new ApplicationResponseDTO().toProcessResult())
        .isInstanceOf(NullPointerException.class);
  }
}
