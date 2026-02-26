package ee.tuleva.onboarding.withdrawals;

import static ee.tuleva.onboarding.auth.JwtTokenGenerator.getHeaders;
import static ee.tuleva.onboarding.auth.PersonFixture.samplePerson;
import static ee.tuleva.onboarding.auth.PersonFixture.sampleRetirementAgePerson;
import static ee.tuleva.onboarding.epis.contact.ContactDetailsFixture.contactDetailsFixture;
import static ee.tuleva.onboarding.pillar.Pillar.SECOND;
import static ee.tuleva.onboarding.pillar.Pillar.THIRD;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import ee.tuleva.onboarding.epis.EpisService;
import ee.tuleva.onboarding.epis.cashflows.CashFlowStatement;
import ee.tuleva.onboarding.epis.withdrawals.ArrestsBankruptciesDto;
import ee.tuleva.onboarding.epis.withdrawals.FundPensionCalculationDto;
import ee.tuleva.onboarding.epis.withdrawals.FundPensionStatusDto;
import ee.tuleva.onboarding.epis.withdrawals.FundPensionStatusDto.FundPensionDto;
import ee.tuleva.onboarding.withdrawals.FundPensionStatus.FundPension;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.client.RestTestClient;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
class WithdrawalsControllerIntegrationTest {

  @Autowired private RestTestClient restTestClient;

  @MockitoBean private EpisService episService;

  @Autowired private JsonMapper mapper;

  @Test
  void testWithdrawalEligibility() {
    var headers = getHeaders();

    var calculation = new FundPensionCalculationDto(20);
    var arrestsBankrupticesDto = new ArrestsBankruptciesDto(false, false);

    when(episService.getCashFlowStatement(any(), any(), any())).thenReturn(new CashFlowStatement());
    when(episService.getContactDetails(any())).thenReturn(contactDetailsFixture());
    when(episService.getFundPensionCalculation(any())).thenReturn(calculation);
    when(episService.getArrestsBankruptciesPresent(any())).thenReturn(arrestsBankrupticesDto);

    restTestClient
        .get()
        .uri("/v1/withdrawals/eligibility")
        .headers(h -> h.addAll(headers))
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.hasReachedEarlyRetirementAge")
        .isEqualTo(false)
        .jsonPath("$.recommendedDurationYears")
        .isEqualTo(calculation.durationYears())
        .jsonPath("$.arrestsOrBankruptciesPresent")
        .isEqualTo(false);
  }

  @Test
  void testFundPensionStatus() throws Exception {
    var headers = getHeaders(sampleRetirementAgePerson);

    var secondPillarFundPensions =
        List.of(new FundPensionDto(Instant.parse("2019-10-01T12:13:27.141Z"), null, 20, true));
    var thirdPillarFundPensions =
        List.of(
            new FundPensionDto(
                Instant.parse("2019-10-01T12:13:27.141Z"),
                Instant.parse("2023-10-01T12:13:27.141Z"),
                20,
                false));
    var fundPensionStatus =
        new FundPensionStatusDto(secondPillarFundPensions, thirdPillarFundPensions);

    when(episService.getCashFlowStatement(any(), any(), any())).thenReturn(new CashFlowStatement());
    when(episService.getContactDetails(any())).thenReturn(contactDetailsFixture());
    when(episService.getFundPensionStatus(any())).thenReturn(fundPensionStatus);

    var responseBody =
        restTestClient
            .get()
            .uri("/v1/withdrawals/fund-pension-status")
            .headers(h -> h.addAll(headers))
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody(byte[].class)
            .returnResult()
            .getResponseBody();

    var jsonNode = mapper.readTree(responseBody);
    var responseFundPensions =
        mapper.readValue(jsonNode.get("fundPensions").toString(), FundPension[].class);
    assertThat(responseFundPensions.length).isEqualTo(2);

    var responseSecondPillarFundPension =
        Arrays.stream(responseFundPensions)
            .filter(fundPension -> fundPension.pillar() == SECOND)
            .findFirst()
            .orElseThrow();

    assertThat(responseSecondPillarFundPension.startDate()).isEqualTo("2019-10-01T12:13:27.141Z");
    assertThat(responseSecondPillarFundPension.endDate()).isNull();
    assertThat(responseSecondPillarFundPension.durationYears()).isEqualTo(20);
    assertThat(responseSecondPillarFundPension.active()).isEqualTo(true);

    var responseThirdPillarFundPension =
        Arrays.stream(responseFundPensions)
            .filter(fundPension -> fundPension.pillar() == THIRD)
            .findFirst()
            .orElseThrow();
    assertThat(responseThirdPillarFundPension.startDate()).isEqualTo("2019-10-01T12:13:27.141Z");
    assertThat(responseThirdPillarFundPension.endDate()).isEqualTo("2023-10-01T12:13:27.141Z");
    assertThat(responseThirdPillarFundPension.durationYears()).isEqualTo(20);
    assertThat(responseThirdPillarFundPension.active()).isEqualTo(false);
  }

  @Test
  void testFundPensionStatusUnder55() {
    var headers = getHeaders(samplePerson);

    when(episService.getCashFlowStatement(any(), any(), any())).thenReturn(new CashFlowStatement());
    when(episService.getContactDetails(any())).thenReturn(contactDetailsFixture());

    restTestClient
        .get()
        .uri("/v1/withdrawals/fund-pension-status")
        .headers(h -> h.addAll(headers))
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.fundPensions.length()")
        .isEqualTo(0);
  }
}
