package ee.tuleva.onboarding.withdrawals;

import static ee.tuleva.onboarding.auth.JwtTokenGenerator.getHeaders;
import static ee.tuleva.onboarding.epis.contact.ContactDetailsFixture.contactDetailsFixture;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpMethod.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ee.tuleva.onboarding.epis.EpisService;
import ee.tuleva.onboarding.epis.cashflows.CashFlowStatement;
import ee.tuleva.onboarding.epis.withdrawals.ArrestsBankruptciesDto;
import ee.tuleva.onboarding.epis.withdrawals.FundPensionCalculationDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class WithdrawalsControllerIntegrationTest {

  @LocalServerPort int randomServerPort;

  @Autowired private TestRestTemplate restTemplate;

  @MockBean private EpisService episService;

  @Test
  @DisplayName("get withdrawal eligibility")
  void testMandateCreation() throws Exception {
    String url = "http://localhost:" + randomServerPort + "/v1/withdrawals/eligibility";

    var headers = getHeaders();

    var calculation = new FundPensionCalculationDto(20);
    var arrestsBankrupticesDto = new ArrestsBankruptciesDto(false, false);

    when(episService.getCashFlowStatement(any(), any(), any())).thenReturn(new CashFlowStatement());
    when(episService.getContactDetails(any())).thenReturn(contactDetailsFixture());
    when(episService.getFundPensionCalculation(any())).thenReturn(calculation);
    when(episService.getArrestsBankruptciesPresent(any())).thenReturn(arrestsBankrupticesDto);

    HttpEntity<Void> requestEntity = new HttpEntity<>(headers);

    ResponseEntity<String> response = restTemplate.exchange(url, GET, requestEntity, String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode jsonNode = (new ObjectMapper()).readTree(response.getBody());
    assertThat(jsonNode.get("hasReachedEarlyRetirementAge").asBoolean()).isEqualTo(false);
    assertThat(jsonNode.get("recommendedDurationYears").asInt())
        .isEqualTo(calculation.durationYears());

    assertThat(jsonNode.get("arrestsOrBankruptciesPresent").asBoolean()).isEqualTo(false);
  }
}
