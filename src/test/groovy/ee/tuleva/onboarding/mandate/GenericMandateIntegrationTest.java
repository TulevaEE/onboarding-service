package ee.tuleva.onboarding.mandate;

// import static ee.tuleva.onboarding.epis.cashflows.CashFlowFixture.cashFlowFixture;
import static ee.tuleva.onboarding.epis.contact.ContactDetailsFixture.contactDetailsFixture;
import static ee.tuleva.onboarding.epis.mandate.details.BankAccountDetails.BankAccountType.ESTONIAN;
import static ee.tuleva.onboarding.epis.mandate.details.Pillar.SECOND;
import static ee.tuleva.onboarding.mandate.MandateFixture.*;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ee.tuleva.onboarding.auth.JwtTokenGenerator;
import ee.tuleva.onboarding.epis.EpisService;
import ee.tuleva.onboarding.epis.cashflows.CashFlowStatement;
import ee.tuleva.onboarding.epis.mandate.GenericMandateCreationDto;
import ee.tuleva.onboarding.epis.mandate.details.*;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.util.Streamable;
import org.springframework.http.*;

@SpringBootTest(webEnvironment = RANDOM_PORT)
class GenericMandateIntegrationTest {

  @LocalServerPort int randomServerPort;

  @Autowired private TestRestTemplate restTemplate;

  @Autowired private MandateRepository mandateRepository;

  @MockBean private EpisService episService;

  static HttpHeaders getHeaders() {
    var jwtToken = JwtTokenGenerator.generateDefaultJwtToken();

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(APPLICATION_JSON);
    headers.add("Authorization", "Bearer " + jwtToken);

    return headers;
  }

  static Stream<Arguments> testMandateDetails() {
    return Stream.of(
        Arguments.of(new TransferCancellationMandateDetails("EE3600109435", SECOND)),
        Arguments.of(new EarlyWithdrawalCancellationMandateDetails()),
        Arguments.of(new WithdrawalCancellationMandateDetails()),
        Arguments.of(
            new FundPensionOpeningMandateDetails(
                SECOND,
                FundPensionOpeningMandateDetails.FundPensionFrequency.MONTHLY,
                new FundPensionOpeningMandateDetails.FundPensionDuration(20, false),
                new BankAccountDetails(ESTONIAN, BankAccountDetails.Bank.LHV, "EE_TEST_IBAN"))));
  }

  @AfterEach
  void cleanup() {
    mandateRepository.deleteAll();
  }

  void assertCanReadMandate(MandateDetails details) {
    List<Mandate> readMandates = Streamable.of(mandateRepository.findAll()).toList();

    assertThat(readMandates.size()).isEqualTo(1);

    Mandate firstMandate = readMandates.getFirst();
    assertThat(firstMandate.getDetails().getMandateType()).isEqualTo(details.getMandateType());
  }

  @Test
  @DisplayName("create generic mandate")
  void testMandateCreation() throws Exception {
    String url = "http://localhost:" + randomServerPort + "/v1/mandates/generic";

    var headers = getHeaders();

    var aDto =
        GenericMandateCreationDto.builder()
            .details(new WithdrawalCancellationMandateDetails())
            .build();

    when(episService.getCashFlowStatement(any(), any(), any())).thenReturn(new CashFlowStatement());
    when(episService.getContactDetails(any())).thenReturn(contactDetailsFixture());

    HttpEntity<GenericMandateCreationDto<?>> request = new HttpEntity<>(aDto, headers);

    ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode jsonNode = (new ObjectMapper()).readTree(response.getBody());
    assertThat(jsonNode.get("details").get("mandateType").asText())
        .isEqualTo("WITHDRAWAL_CANCELLATION");
    assertThat(jsonNode.get("pillar").asInt()).isEqualTo(2);

    assertCanReadMandate(aDto.getDetails());
  }

  @ParameterizedTest
  @DisplayName("create all generic mandate types and fetch")
  @MethodSource("testMandateDetails")
  void testAllMandateDetails(MandateDetails details) {
    String url = "http://localhost:" + randomServerPort + "/v1/mandates/generic";

    var headers = getHeaders();

    var aDto = GenericMandateCreationDto.builder().details(details).build();

    when(episService.getCashFlowStatement(any(), any(), any())).thenReturn(new CashFlowStatement());
    when(episService.getContactDetails(any())).thenReturn(contactDetailsFixture());
    when(episService.getContactDetails(any())).thenReturn(contactDetailsFixture());

    HttpEntity<GenericMandateCreationDto<?>> request = new HttpEntity<>(aDto, headers);

    ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertCanReadMandate(details);
  }
}
