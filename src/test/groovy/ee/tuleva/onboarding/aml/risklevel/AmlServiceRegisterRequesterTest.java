package ee.tuleva.onboarding.aml.risklevel;

import static java.time.Duration.ofSeconds;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;

class AmlServiceRegisterRequesterTest {

  private static final String BASE_URL = "https://aml-service.test";
  private static final String REQUESTER_URL = BASE_URL + "/v1/register-requester";
  private static final String API_TOKEN = "test-api-token-of-at-least-thirty-two-characters";

  private final RestClient.Builder restClientBuilder = RestClient.builder().baseUrl(BASE_URL);
  private final MockRestServiceServer server =
      MockRestServiceServer.bindTo(restClientBuilder).build();
  private final RestClient restClient = restClientBuilder.build();

  @Test
  void namesTheDesignatedPersonAsAnEstonianXRoadUserWithTheBearerToken() {
    server
        .expect(once(), requestTo(REQUESTER_URL))
        .andExpect(method(GET))
        .andExpect(header(AUTHORIZATION, "Bearer " + API_TOKEN))
        .andRespond(withSuccess("{\"personalCode\": \"38812121215\"}", APPLICATION_JSON));

    assertThat(requester(API_TOKEN).designatedXRoadUserId()).contains("EE38812121215");
    server.verify();
  }

  @Test
  void namesNoOneWhenAmlServiceHasNoOneDesignated() {
    server.expect(once(), requestTo(REQUESTER_URL)).andRespond(withStatus(NOT_FOUND));

    assertThat(requester(API_TOKEN).designatedXRoadUserId()).isEqualTo(Optional.empty());
    server.verify();
  }

  @Test
  void asksOnceMoreAfterAServerErrorAndThenRefuses() {
    server.expect(times(2), requestTo(REQUESTER_URL)).andRespond(withServerError());

    assertThatThrownBy(() -> requester(API_TOKEN).designatedXRoadUserId())
        .isInstanceOf(HttpServerErrorException.class);
    server.verify();
  }

  @Test
  void refusesAnyOtherClientErrorWithoutAskingAgain() {
    server.expect(once(), requestTo(REQUESTER_URL)).andRespond(withStatus(FORBIDDEN));

    assertThatThrownBy(() -> requester(API_TOKEN).designatedXRoadUserId())
        .isInstanceOf(HttpClientErrorException.class);
    server.verify();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "null",
        "{}",
        "{\"personalCode\": null}",
        "{\"personalCode\": \"\"}",
        "{\"personalCode\": \"3881212121\"}",
        "{\"personalCode\": \"38812121216\"}",
        "{\"personalCode\": \"EE38812121215\"}"
      })
  void refusesAnAnswerWithoutAValidPersonalCode(String answer) {
    server
        .expect(once(), requestTo(REQUESTER_URL))
        .andRespond(withSuccess(answer, APPLICATION_JSON));

    assertThatThrownBy(() -> requester(API_TOKEN).designatedXRoadUserId())
        .isInstanceOf(IllegalStateException.class);
    server.verify();
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = {"", "   "})
  void sendsNoRequestWithoutAnApiToken(@Nullable String apiToken) {
    assertThatThrownBy(() -> requester(apiToken).designatedXRoadUserId())
        .isInstanceOf(IllegalStateException.class);
    server.verify();
  }

  private AmlServiceRegisterRequester requester(@Nullable String apiToken) {
    return new AmlServiceRegisterRequester(
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
