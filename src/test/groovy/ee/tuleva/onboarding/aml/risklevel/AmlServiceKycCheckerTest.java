package ee.tuleva.onboarding.aml.risklevel;

import static ee.tuleva.onboarding.kyc.KycCheck.RiskLevel.MEDIUM;
import static java.time.Duration.ofSeconds;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.HttpStatus.FOUND;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.transaction.support.TransactionSynchronizationManager.setActualTransactionActive;

import ee.tuleva.onboarding.kyc.KycCheck;
import ee.tuleva.onboarding.user.User;
import java.net.http.HttpTimeoutException;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

class AmlServiceKycCheckerTest {

  private static final String BASE_URL = "https://aml-service.test";
  private static final String ASSESSMENTS_URL = BASE_URL + "/v1/kyc-onboarding/assessments";
  private static final String API_TOKEN = "test-api-token-of-at-least-thirty-two-characters";

  private static final String ASSESSMENT =
      """
      {
        "riskLevel": "MEDIUM",
        "metadata": {
          "version": "1.1.0",
          "level": 2,
          "total_points": 25,
          "is_minor": false,
          "check_24": 0
        }
      }
      """;

  private final User user = User.builder().id(7L).personalCode("38888888888").build();

  private final RestClient.Builder restClientBuilder = RestClient.builder().baseUrl(BASE_URL);
  private final MockRestServiceServer server =
      MockRestServiceServer.bindTo(restClientBuilder).build();
  private final RestClient kycOnboardingRestClient = restClientBuilder.build();

  @Test
  void asksForTheUsersAssessmentByPersonalCodeWithTheBearerToken() {
    server
        .expect(once(), requestTo(ASSESSMENTS_URL))
        .andExpect(method(POST))
        .andExpect(header(AUTHORIZATION, "Bearer " + API_TOKEN))
        .andExpect(content().json("{\"personalCode\": \"38888888888\"}", true))
        .andRespond(withSuccess(ASSESSMENT, APPLICATION_JSON));

    checker(API_TOKEN).check(user);

    server.verify();
  }

  @Test
  void givesTheRiskLevelAndTheMetadataInItsJsonTypes() {
    server.expect(requestTo(ASSESSMENTS_URL)).andRespond(withSuccess(ASSESSMENT, APPLICATION_JSON));

    KycCheck kycCheck = checker(API_TOKEN).check(user);

    assertThat(kycCheck)
        .isEqualTo(
            new KycCheck(
                MEDIUM,
                Map.of(
                    "version",
                    "1.1.0",
                    "level",
                    2,
                    "total_points",
                    25,
                    "is_minor",
                    false,
                    "check_24",
                    0)));
  }

  @Test
  void failsWithoutAskingAgainWhenAmlServiceFindsNoUserOrKycSurvey() {
    server.expect(once(), requestTo(ASSESSMENTS_URL)).andRespond(withResourceNotFound());

    assertThatThrownBy(() -> checker(API_TOKEN).check(user))
        .isInstanceOf(IllegalStateException.class);
    server.verify();
  }

  @Test
  void failsWithoutAskingAgainOnARejectedToken() {
    server.expect(once(), requestTo(ASSESSMENTS_URL)).andRespond(withStatus(UNAUTHORIZED));

    assertThatThrownBy(() -> checker(API_TOKEN).check(user))
        .isInstanceOf(HttpClientErrorException.Unauthorized.class);
    server.verify();
  }

  @Test
  void asksOnceMoreAfterAServerError() {
    server.expect(requestTo(ASSESSMENTS_URL)).andRespond(withServerError());
    server.expect(requestTo(ASSESSMENTS_URL)).andRespond(withSuccess(ASSESSMENT, APPLICATION_JSON));

    assertThat(checker(API_TOKEN).check(user).riskLevel()).isEqualTo(MEDIUM);
    server.verify();
  }

  @Test
  void failsOnAServerErrorThatRepeats() {
    server.expect(times(2), requestTo(ASSESSMENTS_URL)).andRespond(withServerError());

    assertThatThrownBy(() -> checker(API_TOKEN).check(user))
        .isInstanceOf(HttpServerErrorException.class);
    server.verify();
  }

  @Test
  void failsOnATimeoutThatRepeats() {
    server
        .expect(times(2), requestTo(ASSESSMENTS_URL))
        .andRespond(withException(new HttpTimeoutException("request timed out")));

    assertThatThrownBy(() -> checker(API_TOKEN).check(user))
        .isInstanceOf(ResourceAccessException.class);
    server.verify();
  }

  @Test
  void failsOnAnAnswerThatIsNotASuccessEvenWithAnAssessmentInIt() {
    server
        .expect(once(), requestTo(ASSESSMENTS_URL))
        .andRespond(withStatus(FOUND).contentType(APPLICATION_JSON).body(ASSESSMENT));

    assertThatThrownBy(() -> checker(API_TOKEN).check(user))
        .isInstanceOf(IllegalStateException.class);
    server.verify();
  }

  @Test
  void failsOnASuccessWithoutAnAssessment() {
    server.expect(once(), requestTo(ASSESSMENTS_URL)).andRespond(withSuccess());

    assertThatThrownBy(() -> checker(API_TOKEN).check(user))
        .isInstanceOf(IllegalStateException.class);
    server.verify();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "{\"metadata\": {}}",
        "{\"riskLevel\": null, \"metadata\": {}}",
        "{\"riskLevel\": \"LOW\"}",
        "{\"riskLevel\": \"LOW\", \"metadata\": null}"
      })
  void failsOnAnAssessmentMissingARequiredValue(String body) {
    server
        .expect(once(), requestTo(ASSESSMENTS_URL))
        .andRespond(withSuccess(body, APPLICATION_JSON));

    assertThatThrownBy(() -> checker(API_TOKEN).check(user))
        .isInstanceOf(IllegalStateException.class);
    server.verify();
  }

  @Test
  void failsOnAnUnknownRiskLevel() {
    server
        .expect(once(), requestTo(ASSESSMENTS_URL))
        .andRespond(withSuccess("{\"riskLevel\": \"SEVERE\", \"metadata\": {}}", APPLICATION_JSON));

    assertThatThrownBy(() -> checker(API_TOKEN).check(user))
        .isInstanceOf(RestClientException.class);
    server.verify();
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = {"", "   "})
  void sendsNoRequestWithoutAnApiToken(@Nullable String apiToken) {
    assertThatThrownBy(() -> checker(apiToken).check(user))
        .isInstanceOf(IllegalStateException.class);
    server.verify();
  }

  @Test
  void sendsNoRequestInsideATransactionWhoseWritesAmlServiceCannotSee() {
    setActualTransactionActive(true);
    try {
      assertThatThrownBy(() -> checker(API_TOKEN).check(user))
          .isInstanceOf(IllegalStateException.class);
      server.verify();
    } finally {
      setActualTransactionActive(false);
    }
  }

  private AmlServiceKycChecker checker(@Nullable String apiToken) {
    return new AmlServiceKycChecker(
        kycOnboardingRestClient,
        AmlServiceConfiguration.amlServiceRetryTemplate(),
        new AmlServiceProperties(
            BASE_URL,
            apiToken,
            ofSeconds(5),
            ofSeconds(60),
            ofSeconds(1),
            ofSeconds(2),
            ofSeconds(1),
            ofSeconds(5)));
  }
}
