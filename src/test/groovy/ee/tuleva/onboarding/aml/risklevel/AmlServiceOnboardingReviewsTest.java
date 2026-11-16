package ee.tuleva.onboarding.aml.risklevel;

import static ee.tuleva.onboarding.aml.OnboardingReview.Outcome.COMPLETE;
import static ee.tuleva.onboarding.aml.OnboardingReview.Outcome.REJECT;
import static ee.tuleva.onboarding.aml.OnboardingReview.PartyType.LEGAL_ENTITY;
import static ee.tuleva.onboarding.aml.OnboardingReview.PartyType.PERSON;
import static java.time.Duration.ofSeconds;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import ee.tuleva.onboarding.aml.OnboardingReview;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;

class AmlServiceOnboardingReviewsTest {

  private static final String BASE_URL = "https://aml-service.test";
  private static final String OUTCOMES_URL = BASE_URL + "/v1/tkf-onboarding/outcomes";
  private static final String API_TOKEN = "test-api-token-of-at-least-thirty-two-characters";

  private final RestClient.Builder restClientBuilder = RestClient.builder().baseUrl(BASE_URL);
  private final MockRestServiceServer server =
      MockRestServiceServer.bindTo(restClientBuilder).build();
  private final RestClient restClient = restClientBuilder.build();

  @Test
  void asksForTheSettledReviewsWithTheBearerToken() {
    server
        .expect(once(), requestTo(OUTCOMES_URL))
        .andExpect(method(GET))
        .andExpect(header(AUTHORIZATION, "Bearer " + API_TOKEN))
        .andRespond(
            withSuccess(
                """
                [{"code": "38888888888", "type": "PERSON", "outcome": "COMPLETE"},
                 {"code": "12345678", "type": "LEGAL_ENTITY", "outcome": "REJECT"}]
                """,
                APPLICATION_JSON));

    assertThat(reviews(API_TOKEN).settled())
        .containsExactly(
            new OnboardingReview("38888888888", PERSON, COMPLETE),
            new OnboardingReview("12345678", LEGAL_ENTITY, REJECT));
    server.verify();
  }

  @Test
  void asksOnceMoreAfterAServerErrorAndThenRefuses() {
    server.expect(times(2), requestTo(OUTCOMES_URL)).andRespond(withServerError());

    assertThatThrownBy(() -> reviews(API_TOKEN).settled())
        .isInstanceOf(HttpServerErrorException.class);
    server.verify();
  }

  @Test
  void refusesAnAnswerWithoutAList() {
    server
        .expect(once(), requestTo(OUTCOMES_URL))
        .andRespond(withSuccess("null", APPLICATION_JSON));

    assertThatThrownBy(() -> reviews(API_TOKEN).settled())
        .isInstanceOf(IllegalStateException.class);
    server.verify();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "null",
        "{\"type\": \"PERSON\", \"outcome\": \"COMPLETE\"}",
        "{\"code\": \"38888888888\", \"type\": \"PERSON\"}",
        "{\"code\": \"38888888888\", \"type\": \"PERSON\", \"outcome\": null}",
        "{\"code\": \"38888888888\", \"type\": \"PERSON\", \"outcome\": \"WAIT\"}",
        "{\"code\": \"38888888888\", \"outcome\": \"COMPLETE\"}",
        "{\"code\": \"38888888888\", \"type\": \"TRUST\", \"outcome\": \"COMPLETE\"}"
      })
  void leavesOutAnUnusableReviewAndKeepsTheOthers(String unusable) {
    server
        .expect(once(), requestTo(OUTCOMES_URL))
        .andRespond(
            withSuccess(
                "["
                    + unusable
                    + ", {\"code\": \"48888888888\", \"type\": \"PERSON\", \"outcome\":"
                    + " \"REJECT\"}]",
                APPLICATION_JSON));

    assertThat(reviews(API_TOKEN).settled())
        .containsExactly(new OnboardingReview("48888888888", PERSON, REJECT));
    server.verify();
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = {"", "   "})
  void sendsNoRequestWithoutAnApiToken(@Nullable String apiToken) {
    assertThatThrownBy(() -> reviews(apiToken).settled()).isInstanceOf(IllegalStateException.class);
    server.verify();
  }

  private AmlServiceOnboardingReviews reviews(@Nullable String apiToken) {
    return new AmlServiceOnboardingReviews(
        restClient,
        AmlServiceConfiguration.amlServiceRetryTemplate(),
        new AmlServiceProperties(
            BASE_URL,
            apiToken,
            ofSeconds(5),
            ofSeconds(60),
            ofSeconds(1),
            ofSeconds(2),
            ofSeconds(1),
            ofSeconds(3)));
  }
}
