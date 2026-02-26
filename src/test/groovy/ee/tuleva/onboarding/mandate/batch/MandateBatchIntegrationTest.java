package ee.tuleva.onboarding.mandate.batch;

import static ee.tuleva.onboarding.auth.JwtTokenGenerator.getHeaders;
import static ee.tuleva.onboarding.epis.contact.ContactDetailsFixture.contactDetailsFixture;
import static ee.tuleva.onboarding.mandate.MandateType.FUND_PENSION_OPENING;
import static ee.tuleva.onboarding.mandate.MandateType.PARTIAL_WITHDRAWAL;
import static ee.tuleva.onboarding.mandate.batch.MandateBatchStatus.INITIALIZED;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

import ee.tuleva.onboarding.aml.AmlAutoChecker;
import ee.tuleva.onboarding.epis.EpisService;
import ee.tuleva.onboarding.epis.cashflows.CashFlowStatement;
import ee.tuleva.onboarding.mandate.MandateFixture;
import ee.tuleva.onboarding.mandate.MandateRepository;
import ee.tuleva.onboarding.mandate.generic.MandateDto;
import ee.tuleva.onboarding.withdrawals.WithdrawalEligibilityDto;
import ee.tuleva.onboarding.withdrawals.WithdrawalEligibilityService;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.util.Streamable;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.client.RestTestClient;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(webEnvironment = RANDOM_PORT)
@AutoConfigureRestTestClient
class MandateBatchIntegrationTest {

  @Autowired private RestTestClient restTestClient;

  @Autowired private JsonMapper mapper;

  @Autowired private MandateBatchRepository mandateBatchRepository;
  @Autowired private MandateRepository mandateRepository;

  @MockitoBean private EpisService episService;
  @MockitoBean private AmlAutoChecker amlAutoChecker;
  @MockitoBean private WithdrawalEligibilityService withdrawalEligibilityService;

  @AfterEach
  void cleanup() {
    mandateRepository.deleteAll();
    mandateBatchRepository.deleteAll();
  }

  void assertCorrectResponse(byte[] responseBody) throws Exception {
    var jsonNode = mapper.readTree(responseBody);

    MandateDto[] responseMandateDtos =
        mapper.readValue(jsonNode.get("mandates").toString(), MandateDto[].class);

    assertThat(responseMandateDtos.length).isEqualTo(2);
    assertThat(
            Streamable.of(responseMandateDtos).stream()
                .filter(mandateDto -> mandateDto.getMandateType().equals(PARTIAL_WITHDRAWAL))
                .findFirst())
        .isPresent();
    assertThat(
            Streamable.of(responseMandateDtos).stream()
                .filter(mandateDto -> mandateDto.getMandateType().equals(FUND_PENSION_OPENING))
                .findFirst())
        .isPresent();
  }

  void assertCanReadMandateBatch() {
    var readMandateBatches = Streamable.of(mandateBatchRepository.findAll()).toList();
    assertThat(readMandateBatches.size()).isEqualTo(1);
    MandateBatch firstMandateBatch = readMandateBatches.getFirst();

    assertThat(firstMandateBatch.getStatus()).isEqualTo(INITIALIZED);
    var firstMandateBatchMandates = firstMandateBatch.getMandates();

    assertThat(firstMandateBatchMandates.size()).isEqualTo(2);
    assertThat(
            Streamable.of(firstMandateBatchMandates).stream()
                .filter(mandate -> mandate.getMandateType().equals(PARTIAL_WITHDRAWAL))
                .findFirst())
        .isPresent();
    assertThat(
            Streamable.of(firstMandateBatchMandates).stream()
                .filter(mandate -> mandate.getMandateType().equals(FUND_PENSION_OPENING))
                .findFirst())
        .isPresent();
  }

  @Test
  void testMandateCreation() throws Exception {
    var headers = getHeaders();

    var aFundPensionOpeningMandateDetails = MandateFixture.aFundPensionOpeningMandateDetails;
    var aPartialWithdrawalMandateDetails = MandateFixture.aPartialWithdrawalMandateDetails;

    var aDto =
        MandateBatchDto.builder()
            .mandates(
                List.of(
                    MandateDto.builder().details(aFundPensionOpeningMandateDetails).build(),
                    MandateDto.builder().details(aPartialWithdrawalMandateDetails).build()))
            .build();

    var aWithdrawalEligibility =
        WithdrawalEligibilityDto.builder()
            .hasReachedEarlyRetirementAge(true)
            .canWithdrawThirdPillarWithReducedTax(true)
            .age(65)
            .recommendedDurationYears(20)
            .arrestsOrBankruptciesPresent(false)
            .build();

    when(withdrawalEligibilityService.getWithdrawalEligibility(any()))
        .thenReturn(aWithdrawalEligibility);
    when(episService.getCashFlowStatement(any(), any(), any())).thenReturn(new CashFlowStatement());
    when(episService.getContactDetails(any())).thenReturn(contactDetailsFixture());

    var responseBody =
        restTestClient
            .post()
            .uri("/v1/mandate-batches")
            .headers(h -> h.addAll(headers))
            .body(aDto)
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody(byte[].class)
            .returnResult()
            .getResponseBody();

    assertCorrectResponse(responseBody);
    assertCanReadMandateBatch();
  }

  @Test
  void testMandateCreationBeforeRetirementAge() {
    var headers = getHeaders();

    var aFundPensionOpeningMandateDetails = MandateFixture.aFundPensionOpeningMandateDetails;
    var aPartialWithdrawalMandateDetails = MandateFixture.aPartialWithdrawalMandateDetails;

    var aDto =
        MandateBatchDto.builder()
            .mandates(
                List.of(
                    MandateDto.builder().details(aFundPensionOpeningMandateDetails).build(),
                    MandateDto.builder().details(aPartialWithdrawalMandateDetails).build()))
            .build();

    var aWithdrawalEligibility =
        WithdrawalEligibilityDto.builder()
            .hasReachedEarlyRetirementAge(false)
            .canWithdrawThirdPillarWithReducedTax(false)
            .age(30)
            .recommendedDurationYears(55)
            .arrestsOrBankruptciesPresent(false)
            .build();

    when(withdrawalEligibilityService.getWithdrawalEligibility(any()))
        .thenReturn(aWithdrawalEligibility);
    when(episService.getCashFlowStatement(any(), any(), any())).thenReturn(new CashFlowStatement());
    when(episService.getContactDetails(any())).thenReturn(contactDetailsFixture());

    restTestClient
        .post()
        .uri("/v1/mandate-batches")
        .headers(h -> h.addAll(headers))
        .body(aDto)
        .exchange()
        .expectStatus()
        .value(status -> assertThat(status).isNotEqualTo(200));

    assertThat(Streamable.of(mandateBatchRepository.findAll()).toList().size()).isEqualTo(0);
  }

  @Test
  void testMandateCreationThirdPillarSpecialCase() throws Exception {
    var headers = getHeaders();

    var aFundPensionOpeningMandateDetails =
        MandateFixture.aThirdPillarFundPensionOpeningMandateDetails;
    var aPartialWithdrawalMandateDetails =
        MandateFixture.aThirdPillarPartialWithdrawalMandateDetails;

    var aDto =
        MandateBatchDto.builder()
            .mandates(
                List.of(
                    MandateDto.builder().details(aFundPensionOpeningMandateDetails).build(),
                    MandateDto.builder().details(aPartialWithdrawalMandateDetails).build()))
            .build();

    var aWithdrawalEligibility =
        WithdrawalEligibilityDto.builder()
            .hasReachedEarlyRetirementAge(false)
            .canWithdrawThirdPillarWithReducedTax(true)
            .age(56)
            .recommendedDurationYears(24)
            .arrestsOrBankruptciesPresent(false)
            .build();

    when(withdrawalEligibilityService.getWithdrawalEligibility(any()))
        .thenReturn(aWithdrawalEligibility);
    when(episService.getCashFlowStatement(any(), any(), any())).thenReturn(new CashFlowStatement());
    when(episService.getContactDetails(any())).thenReturn(contactDetailsFixture());

    var responseBody =
        restTestClient
            .post()
            .uri("/v1/mandate-batches")
            .headers(h -> h.addAll(headers))
            .body(aDto)
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody(byte[].class)
            .returnResult()
            .getResponseBody();

    assertCorrectResponse(responseBody);
    assertCanReadMandateBatch();
  }

  @Test
  void testMandateCreationThirdPillarSpecialCaseDisabled() {
    var headers = getHeaders();

    var aFundPensionOpeningMandateDetails =
        MandateFixture.aThirdPillarFundPensionOpeningMandateDetails;
    var aPartialWithdrawalMandateDetails =
        MandateFixture.aThirdPillarPartialWithdrawalMandateDetails;

    var aDto =
        MandateBatchDto.builder()
            .mandates(
                List.of(
                    MandateDto.builder().details(aFundPensionOpeningMandateDetails).build(),
                    MandateDto.builder().details(aPartialWithdrawalMandateDetails).build()))
            .build();

    var aWithdrawalEligibility =
        WithdrawalEligibilityDto.builder()
            .hasReachedEarlyRetirementAge(false)
            .canWithdrawThirdPillarWithReducedTax(false)
            .age(56)
            .recommendedDurationYears(24)
            .arrestsOrBankruptciesPresent(false)
            .build();

    when(withdrawalEligibilityService.getWithdrawalEligibility(any()))
        .thenReturn(aWithdrawalEligibility);
    when(episService.getCashFlowStatement(any(), any(), any())).thenReturn(new CashFlowStatement());
    when(episService.getContactDetails(any())).thenReturn(contactDetailsFixture());

    restTestClient
        .post()
        .uri("/v1/mandate-batches")
        .headers(h -> h.addAll(headers))
        .body(aDto)
        .exchange()
        .expectStatus()
        .value(status -> assertThat(status).isNotEqualTo(200));

    assertThat(Streamable.of(mandateBatchRepository.findAll()).toList().size()).isEqualTo(0);
  }
}
