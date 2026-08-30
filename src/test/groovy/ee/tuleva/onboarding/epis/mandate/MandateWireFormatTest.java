package ee.tuleva.onboarding.epis.mandate;

import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.applicationtype.ApplicationType;
import ee.tuleva.onboarding.country.Country;
import ee.tuleva.onboarding.epis.application.ApplicationResponse;
import ee.tuleva.onboarding.epis.mandate.ApplicationDTO.FundPensionDetails;
import ee.tuleva.onboarding.epis.mandate.MandateDto.MandateFundsTransferExchangeDTO;
import ee.tuleva.onboarding.epis.mandate.command.MandateCommand;
import ee.tuleva.onboarding.epis.mandate.command.MandateCommandResponse;
import ee.tuleva.onboarding.mandate.details.BankAccountDetails;
import ee.tuleva.onboarding.mandate.details.BankAccountDetails.BankAccountType;
import ee.tuleva.onboarding.mandate.details.EarlyWithdrawalCancellationMandateDetails;
import ee.tuleva.onboarding.mandate.details.FundPensionOpeningMandateDetails;
import ee.tuleva.onboarding.mandate.details.FundPensionOpeningMandateDetails.FundPensionDuration;
import ee.tuleva.onboarding.mandate.details.PartialWithdrawalMandateDetails;
import ee.tuleva.onboarding.mandate.details.PartialWithdrawalMandateDetails.FundWithdrawalAmount;
import ee.tuleva.onboarding.mandate.details.PaymentRateChangeMandateDetails;
import ee.tuleva.onboarding.mandate.details.PaymentRateChangeMandateDetails.PaymentRate;
import ee.tuleva.onboarding.mandate.details.SelectionMandateDetails;
import ee.tuleva.onboarding.mandate.details.TransferCancellationMandateDetails;
import ee.tuleva.onboarding.mandate.details.WithdrawalCancellationMandateDetails;
import ee.tuleva.onboarding.pillar.Pillar;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import tools.jackson.databind.json.JsonMapper;

@JsonTest
class MandateWireFormatTest {

  @Autowired private JsonMapper jsonMapper;

  private void assertJsonEquals(String actualJson, String expectedJson) {
    assertThat(jsonMapper.readTree(actualJson)).isEqualTo(jsonMapper.readTree(expectedJson));
  }

  @Test
  void sendMandateSerializesAllLegacyFields() {
    MandateDto dto =
        MandateDto.builder()
            .id(123L)
            .processId("processId1")
            .futureContributionFundIsin("EE3600109435")
            .createdDate(Instant.parse("2021-03-09T10:00:00Z"))
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

    String json = jsonMapper.writeValueAsString(dto);

    assertJsonEquals(
        json,
        """
        {"address":{"countryCode":"EE"},"createdDate":"2021-03-09T10:00:00Z",
        "email":"test@tuleva.ee","fundTransferExchanges":[{"processId":"processId2",
        "amount":1.5,"sourceFundIsin":"EE1","targetFundIsin":"EE2","targetPik":null},
        {"processId":"processId3","amount":null,"sourceFundIsin":"EE3","targetFundIsin":null,
        "targetPik":"EE471000001020145685"}],"futureContributionFundIsin":"EE3600109435",
        "id":123,"paymentRate":4,"phoneNumber":"+37288888888","pillar":2,
        "processId":"processId1"}
        """);
  }

  @Test
  void sendMandateV2SerializesFundPensionOpeningDetails() {
    var details =
        new FundPensionOpeningMandateDetails(
            Pillar.SECOND,
            new FundPensionDuration(20, true),
            new BankAccountDetails(BankAccountType.ESTONIAN, "EE471000001020145685"));
    GenericMandateDto<FundPensionOpeningMandateDetails> dto =
        GenericMandateDto.<FundPensionOpeningMandateDetails>builder()
            .id(875L)
            .createdDate(Instant.parse("2021-03-09T10:00:00Z"))
            .address(new Country("EE"))
            .email("test@tuleva.ee")
            .phoneNumber("+37288888888")
            .details(details)
            .build();
    MandateCommand<FundPensionOpeningMandateDetails> command =
        new MandateCommand<>("processId1", dto);

    String json = jsonMapper.writeValueAsString(command);

    assertJsonEquals(
        json,
        """
        {"processId":"processId1","mandateDto":{"address":{"countryCode":"EE"},
        "createdDate":"2021-03-09T10:00:00Z","details":{"pillar":"SECOND",
        "duration":{"durationYears":20,"recommendedDuration":true},
        "bankAccountDetails":{"type":"ESTONIAN","accountIban":"EE471000001020145685"},
        "mandateType":"FUND_PENSION_OPENING"},"email":"test@tuleva.ee","id":875,
        "phoneNumber":"+37288888888"}}
        """);
  }

  @Test
  void sendMandateV2SerializesPartialWithdrawalDetails() {
    var details =
        new PartialWithdrawalMandateDetails(
            Pillar.SECOND,
            new BankAccountDetails(BankAccountType.ESTONIAN, "EE471000001020145685"),
            List.of(new FundWithdrawalAmount("EE3600109435", 100, BigDecimal.valueOf(12.345))),
            "EST");

    String json = jsonMapper.writeValueAsString(details);

    assertJsonEquals(
        json,
        """
        {"pillar":"SECOND","bankAccountDetails":{"type":"ESTONIAN",
        "accountIban":"EE471000001020145685"},"fundWithdrawalAmounts":[
        {"isin":"EE3600109435","percentage":100,"units":12.345}],"taxResidency":"EST",
        "mandateType":"PARTIAL_WITHDRAWAL"}
        """);
  }

  @Test
  void sendMandateV2SerializesPaymentRateChangeDetails() {
    var details = new PaymentRateChangeMandateDetails(PaymentRate.FOUR);

    String json = jsonMapper.writeValueAsString(details);

    assertJsonEquals(
        json,
        """
        {"paymentRate":"FOUR","mandateType":"PAYMENT_RATE_CHANGE"}
        """);
  }

  @Test
  void sendMandateV2SerializesTransferCancellationDetails() {
    var details = new TransferCancellationMandateDetails("EE3600109435", Pillar.SECOND);

    String json = jsonMapper.writeValueAsString(details);

    assertJsonEquals(
        json,
        """
        {"sourceFundIsinOfTransferToCancel":"EE3600109435","pillar":"SECOND",
        "mandateType":"TRANSFER_CANCELLATION"}
        """);
  }

  @Test
  void sendMandateV2SerializesEarlyWithdrawalCancellationDetails() {
    String json = jsonMapper.writeValueAsString(new EarlyWithdrawalCancellationMandateDetails());

    assertJsonEquals(
        json,
        """
        {"mandateType":"EARLY_WITHDRAWAL_CANCELLATION"}
        """);
  }

  @Test
  void sendMandateV2SerializesWithdrawalCancellationDetails() {
    String json = jsonMapper.writeValueAsString(new WithdrawalCancellationMandateDetails());

    assertJsonEquals(
        json,
        """
        {"mandateType":"WITHDRAWAL_CANCELLATION"}
        """);
  }

  @Test
  void sendMandateV2SerializesSelectionDetails() {
    String json = jsonMapper.writeValueAsString(new SelectionMandateDetails("EE3600109435"));

    assertJsonEquals(
        json,
        """
        {"futureContributionFundIsin":"EE3600109435","mandateType":"SELECTION"}
        """);
  }

  @Test
  void receivesTransferApplicationDto() {
    String json =
        """
        {"currency":"EUR","date":"2021-03-09T10:00:00Z","id":123,
        "documentNumber":"DOC-1","status":"PENDING","sourceFundIsin":"EE3600109435",
        "fundTransferExchanges":[{"processId":"processId2","amount":1.5,
        "sourceFundIsin":"EE3600109435","targetFundIsin":"EE3600109436","targetPik":null},
        {"processId":"processId3","amount":null,"sourceFundIsin":"EE3600109435",
        "targetFundIsin":null,"targetPik":"EE471000001020145685"}],
        "type":"TRANSFER","bankAccount":null,"paymentRate":null,"fundPensionDetails":null}
        """;

    ApplicationDTO actual = jsonMapper.readValue(json, ApplicationDTO.class);

    ApplicationDTO expected =
        ApplicationDTO.builder()
            .currency("EUR")
            .date(Instant.parse("2021-03-09T10:00:00Z"))
            .id(123L)
            .documentNumber("DOC-1")
            .status(ApplicationStatus.PENDING)
            .sourceFundIsin("EE3600109435")
            .fundTransferExchanges(
                List.of(
                    new MandateFundsTransferExchangeDTO(
                        "processId2",
                        BigDecimal.valueOf(1.5),
                        "EE3600109435",
                        "EE3600109436",
                        null),
                    new MandateFundsTransferExchangeDTO(
                        "processId3", null, "EE3600109435", null, "EE471000001020145685")))
            .type(ApplicationType.TRANSFER)
            .build();

    assertThat(actual).usingRecursiveComparison().isEqualTo(expected);
    assertThat(actual.isWithdrawal()).isFalse();
  }

  @Test
  void receivesWithdrawalApplicationDto() {
    String json =
        """
        {"date":"2021-03-09T10:00:00Z","id":124,"status":"PENDING",
        "type":"WITHDRAWAL","bankAccount":"EE471000001020145685"}
        """;

    ApplicationDTO actual = jsonMapper.readValue(json, ApplicationDTO.class);

    ApplicationDTO expected =
        ApplicationDTO.builder()
            .date(Instant.parse("2021-03-09T10:00:00Z"))
            .id(124L)
            .status(ApplicationStatus.PENDING)
            .type(ApplicationType.WITHDRAWAL)
            .bankAccount("EE471000001020145685")
            .build();

    assertThat(actual).usingRecursiveComparison().isEqualTo(expected);
    assertThat(actual.isWithdrawal()).isTrue();
  }

  @Test
  void receivesFundPensionOpeningApplicationDto() {
    String json =
        """
        {"date":"2021-03-09T10:00:00Z","id":125,"status":"COMPLETE",
        "type":"FUND_PENSION_OPENING","bankAccount":"EE471000001020145685",
        "fundPensionDetails":{"durationYears":20,"paymentsPerYear":12}}
        """;

    ApplicationDTO actual = jsonMapper.readValue(json, ApplicationDTO.class);

    ApplicationDTO expected =
        ApplicationDTO.builder()
            .date(Instant.parse("2021-03-09T10:00:00Z"))
            .id(125L)
            .status(ApplicationStatus.COMPLETE)
            .type(ApplicationType.FUND_PENSION_OPENING)
            .bankAccount("EE471000001020145685")
            .fundPensionDetails(new FundPensionDetails(20, 12))
            .build();

    assertThat(actual).usingRecursiveComparison().isEqualTo(expected);
  }

  @Test
  void receivesPaymentRateApplicationDto() {
    String json =
        """
        {"date":"2021-03-09T10:00:00Z","id":126,"status":"PENDING",
        "type":"PAYMENT_RATE","paymentRate":6}
        """;

    ApplicationDTO actual = jsonMapper.readValue(json, ApplicationDTO.class);

    ApplicationDTO expected =
        ApplicationDTO.builder()
            .date(Instant.parse("2021-03-09T10:00:00Z"))
            .id(126L)
            .status(ApplicationStatus.PENDING)
            .type(ApplicationType.PAYMENT_RATE)
            .paymentRate(BigDecimal.valueOf(6))
            .build();

    assertThat(actual).usingRecursiveComparison().isEqualTo(expected);
    assertThat(actual.isPaymentRate()).isTrue();
  }

  @Test
  void receivesApplicationResponseDto() {
    String json =
        """
        {"mandateResponses":[{"successful":true,"errorCode":null,"errorMessage":null,
        "applicationType":"TRANSFER","processId":"processId2"},
        {"successful":false,"errorCode":7,"errorMessage":"failure",
        "applicationType":"WITHDRAWAL","processId":"processId3"}]}
        """;

    ApplicationResponseDTO actual = jsonMapper.readValue(json, ApplicationResponseDTO.class);

    List<ApplicationResponse> expected =
        List.of(
            ApplicationResponse.builder()
                .successful(true)
                .applicationType(ApplicationType.TRANSFER)
                .processId("processId2")
                .build(),
            ApplicationResponse.builder()
                .successful(false)
                .errorCode(7)
                .errorMessage("failure")
                .applicationType(ApplicationType.WITHDRAWAL)
                .processId("processId3")
                .build());

    assertThat(actual.getMandateResponses()).usingRecursiveComparison().isEqualTo(expected);
  }

  @Test
  void receivesSuccessfulMandateCommandResponse() {
    String json =
        """
        {"processId":"processId1","successful":true,"errorCode":null,"errorMessage":null}
        """;

    MandateCommandResponse actual = jsonMapper.readValue(json, MandateCommandResponse.class);

    assertThat(actual.getProcessId()).isEqualTo("processId1");
    assertThat(actual.isSuccessful()).isTrue();
    assertThat(actual.getErrorCode()).isNull();
    assertThat(actual.getErrorMessage()).isNull();
  }

  @Test
  void receivesFailedMandateCommandResponse() {
    String json =
        """
        {"processId":"processId2","successful":false,"errorCode":7,
        "errorMessage":"mandate rejected"}
        """;

    MandateCommandResponse actual = jsonMapper.readValue(json, MandateCommandResponse.class);

    assertThat(actual.getProcessId()).isEqualTo("processId2");
    assertThat(actual.isSuccessful()).isFalse();
    assertThat(actual.getErrorCode()).isEqualTo(7);
    assertThat(actual.getErrorMessage()).isEqualTo("mandate rejected");
  }

  @Test
  void applicationStatusWireNamesAreStable() {
    assertThat(ApplicationStatus.valueOf("COMPLETE")).isEqualTo(ApplicationStatus.COMPLETE);
    assertThat(ApplicationStatus.valueOf("PENDING")).isEqualTo(ApplicationStatus.PENDING);
    assertThat(ApplicationStatus.valueOf("FAILED")).isEqualTo(ApplicationStatus.FAILED);
    assertThat(ApplicationStatus.COMPLETE.name()).isEqualTo("COMPLETE");
    assertThat(ApplicationStatus.PENDING.name()).isEqualTo("PENDING");
    assertThat(ApplicationStatus.FAILED.name()).isEqualTo("FAILED");

    assertThat(ApplicationStatus.COMPLETE.isComplete()).isTrue();
    assertThat(ApplicationStatus.COMPLETE.isPending()).isFalse();
    assertThat(ApplicationStatus.PENDING.isPending()).isTrue();
    assertThat(ApplicationStatus.PENDING.isComplete()).isFalse();
    assertThat(ApplicationStatus.FAILED.isPending()).isFalse();
    assertThat(ApplicationStatus.FAILED.isComplete()).isFalse();
  }
}
