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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.util.Streamable;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

// Signing tests are in MandateBatchSigningController (non-integration test)
@SpringBootTest(webEnvironment = RANDOM_PORT)
@AutoConfigureTestRestTemplate
public class MandateBatchIntegrationTest {

  @LocalServerPort int randomServerPort;

  @Autowired private TestRestTemplate restTemplate;

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

  void assertCorrectResponse(ResponseEntity<String> response) throws JsonProcessingException {
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

    ObjectMapper mapper = new ObjectMapper();
    JsonNode jsonNode = mapper.readTree(response.getBody());

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
  @DisplayName("create mandate batch")
  void testMandateCreation() throws Exception {
    String url = "http://localhost:" + randomServerPort + "/v1/mandate-batches";

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

    HttpEntity<MandateBatchDto> request = new HttpEntity<>(aDto, headers);

    ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);

    assertCorrectResponse(response);
    assertCanReadMandateBatch();
  }

  @Test
  @DisplayName("create mandate batch throws before retirement")
  void testMandateCreationBeforeRetirementAge() {
    String url = "http://localhost:" + randomServerPort + "/v1/mandate-batches";

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

    HttpEntity<MandateBatchDto> request = new HttpEntity<>(aDto, headers);

    ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);

    assertThat(response.getStatusCode()).isNotEqualTo(HttpStatus.OK);
    assertThat(Streamable.of(mandateBatchRepository.findAll()).toList().size()).isEqualTo(0);
  }

  @Test
  @DisplayName("create mandate batch works for III pillar special case at 55+")
  void testMandateCreationThirdPillarSpecialCase() throws JsonProcessingException {
    String url = "http://localhost:" + randomServerPort + "/v1/mandate-batches";

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

    HttpEntity<MandateBatchDto> request = new HttpEntity<>(aDto, headers);

    ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);

    assertCorrectResponse(response);
    assertCanReadMandateBatch();
  }

  @Test
  @DisplayName("create mandate batch doesn't work when III pillar special case isn't available")
  void testMandateCreationThirdPillarSpecialCaseDisabled() {
    String url = "http://localhost:" + randomServerPort + "/v1/mandate-batches";

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

    HttpEntity<MandateBatchDto> request = new HttpEntity<>(aDto, headers);

    ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);

    assertThat(response.getStatusCode()).isNotEqualTo(HttpStatus.OK);
    assertThat(Streamable.of(mandateBatchRepository.findAll()).toList().size()).isEqualTo(0);
  }
}
