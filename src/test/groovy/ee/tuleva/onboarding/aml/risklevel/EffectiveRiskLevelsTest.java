package ee.tuleva.onboarding.aml.risklevel;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.time.Duration.ofSeconds;
import static java.time.temporal.ChronoUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Named.named;
import static org.springframework.http.HttpHeaders.ACCEPT;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.FOUND;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.REQUEST_TIMEOUT;
import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;
import static org.springframework.http.HttpStatus.TOO_MANY_REQUESTS;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.sun.net.httpserver.HttpServer;
import ee.tuleva.onboarding.aml.RiskLevelUnavailableException;
import ee.tuleva.onboarding.time.MutableClock;
import java.io.IOException;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.Timeout.ThreadMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.ResponseCreator;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

class EffectiveRiskLevelsTest {

  private static final String BASE_URL = "https://aml-service.test";
  private static final String PERSONAL_CODE = "38888888888";
  private static final String OTHER_PERSONAL_CODE = "49999999990";
  private static final String EFFECTIVE_URL =
      BASE_URL + "/v1/risk-levels/effective/" + PERSONAL_CODE;
  private static final String OTHER_EFFECTIVE_URL =
      BASE_URL + "/v1/risk-levels/effective/" + OTHER_PERSONAL_CODE;
  private static final String REGISTRY_CODE = "16001234";
  private static final String OTHER_REGISTRY_CODE = "16005678";
  private static final String COMPANY_EFFECTIVE_URL =
      BASE_URL + "/v1/risk-levels/effective/companies/" + REGISTRY_CODE;
  private static final String OTHER_COMPANY_EFFECTIVE_URL =
      BASE_URL + "/v1/risk-levels/effective/companies/" + OTHER_REGISTRY_CODE;
  private static final String API_TOKEN = "test-api-token-of-at-least-thirty-two-characters";
  private static final Duration STALL = Duration.ofSeconds(10);

  private final RestClient.Builder restClientBuilder = RestClient.builder().baseUrl(BASE_URL);
  private final MockRestServiceServer server =
      MockRestServiceServer.bindTo(restClientBuilder).build();
  private final RestClient restClient = restClientBuilder.build();
  private final MutableClock clock = new MutableClock();

  @Test
  void asksForThePersonsEffectiveLevelsWithTheBearerToken() {
    server
        .expect(once(), requestTo(EFFECTIVE_URL))
        .andExpect(method(GET))
        .andExpect(header(AUTHORIZATION, "Bearer " + API_TOKEN))
        .andExpect(header(ACCEPT, APPLICATION_JSON_VALUE))
        .andRespond(withSuccess(families(family("tkf", "LOW")), APPLICATION_JSON));

    effectiveRiskLevels(API_TOKEN).isHighRisk(PERSONAL_CODE);

    server.verify();
  }

  @Test
  void highRiskWhenOnlyTheTkfFamilyIsEffectivelyHigh() {
    respond(
        families(
            family("third-pillar", "LOW"),
            """
            {"family": "tkf", "computed": "MEDIUM", "effective": "HIGH", "override": null}
            """));

    assertThat(effectiveRiskLevels(API_TOKEN).isHighRisk(PERSONAL_CODE)).isTrue();
  }

  @Test
  void notHighRiskWhenTheSpecialistOverrodeAComputedHighToLow() {
    respond(
        families(
            """
            {"family": "third-pillar", "computed": "HIGH", "effective": "LOW",
             "override": {"outcome": "OVERRIDDEN", "level": "LOW",
                          "createdAt": "2026-11-02T10:00:00Z"}}
            """));

    assertThat(effectiveRiskLevels(API_TOKEN).isHighRisk(PERSONAL_CODE)).isFalse();
  }

  @Test
  void notHighRiskWhenEveryFamilyIsEffectivelyLowOrMedium() {
    respond(families(family("third-pillar", "LOW"), family("tkf", "MEDIUM")));

    assertThat(effectiveRiskLevels(API_TOKEN).isHighRisk(PERSONAL_CODE)).isFalse();
  }

  @Test
  void notHighRiskWhenThePersonHasNoLevelInAnyFamily() {
    respond(families());

    assertThat(effectiveRiskLevels(API_TOKEN).isHighRisk(PERSONAL_CODE)).isFalse();
  }

  @ParameterizedTest
  @MethodSource("unusableFamilies")
  void highRiskWhenAnotherFamilyIsHighEvenIfThisOneIsUnusable(String unusableFamily) {
    respond(families(unusableFamily, family("tkf", "HIGH")));

    assertThat(effectiveRiskLevels(API_TOKEN).isHighRisk(PERSONAL_CODE)).isTrue();
  }

  static Stream<Named<String>> unusableFamilies() {
    return Stream.of(
        named("unknown level", family("third-pillar", "EXTREME")),
        named("null family", "null"),
        named("no effective level", "{\"family\": \"third-pillar\", \"computed\": \"LOW\"}"),
        named("null effective level", "{\"family\": \"third-pillar\", \"effective\": null}"));
  }

  @ParameterizedTest
  @MethodSource("failuresAndTheirReasons")
  void namesAFailureByItsStatusOrClassesWithoutAnyMessage(RuntimeException failure, String reason) {
    assertThat(EffectiveRiskLevels.reasonWithoutPersonalData(failure)).isEqualTo(reason);
  }

  @Test
  @Timeout(value = 5, unit = TimeUnit.SECONDS, threadMode = ThreadMode.SEPARATE_THREAD)
  void namesAFailureWhoseCausesFormACycle() {
    var first = new IOException("first");
    var second = new IOException("second", first);
    first.initCause(second);

    assertThat(
            EffectiveRiskLevels.reasonWithoutPersonalData(
                new ResourceAccessException("I/O error", first)))
        .isEqualTo("ResourceAccessException(IOException)");
  }

  static Stream<Arguments> failuresAndTheirReasons() {
    var requestFailed = "I/O error on GET request for \"" + EFFECTIVE_URL + "\"";
    return Stream.of(
        Arguments.of(
            named(
                "timeout",
                new ResourceAccessException(
                    requestFailed, new HttpTimeoutException("request timed out"))),
            "ResourceAccessException(HttpTimeoutException)"),
        Arguments.of(
            named(
                "refused connection",
                new ResourceAccessException(
                    requestFailed,
                    new IOException(requestFailed, new ConnectException("Connection refused")))),
            "ResourceAccessException(ConnectException)"),
        Arguments.of(
            named(
                "stalled body",
                new RestClientException(
                    "Error while extracting response", new IOException("body read timed out"))),
            "RestClientException(IOException)"),
        Arguments.of(
            named(
                "server error",
                HttpServerErrorException.create(
                    INTERNAL_SERVER_ERROR, requestFailed, HttpHeaders.EMPTY, new byte[0], null)),
            "HTTP 500"),
        Arguments.of(
            named(
                "unusable answer",
                new UnusableRiskLevelAnswerException(
                    "aml-service answered without a required value: field=families")),
            "aml-service answered without a required value: field=families"),
        Arguments.of(
            named("anything else", new IllegalStateException(requestFailed)),
            "IllegalStateException"));
  }

  @ParameterizedTest
  @MethodSource("unanswered")
  void unavailableAfterOneRequestWithoutCarryingTheHttpFailureWhenAmlServiceCannotAnswer(
      ResponseCreator unanswered) {
    server.expect(once(), requestTo(EFFECTIVE_URL)).andRespond(unanswered);

    assertThatThrownBy(() -> effectiveRiskLevels(API_TOKEN).isHighRisk(PERSONAL_CODE))
        .isInstanceOf(RiskLevelUnavailableException.class)
        .hasNoCause();
    server.verify();
  }

  static Stream<Named<ResponseCreator>> unanswered() {
    return Stream.concat(failingEveryone(), failingThisPersonOnly());
  }

  static Stream<Named<ResponseCreator>> failingEveryone() {
    return Stream.of(
        named("server error", withServerError()),
        named("unavailable", withStatus(SERVICE_UNAVAILABLE)),
        named("timeout", withException(new HttpTimeoutException("request timed out"))),
        named("rejected token", withStatus(UNAUTHORIZED)),
        named("token not allowed", withStatus(FORBIDDEN)),
        named("rate limited", withStatus(TOO_MANY_REQUESTS)),
        named("not found", withStatus(NOT_FOUND)),
        named("request timeout", withStatus(REQUEST_TIMEOUT)),
        named(
            "levels in a redirect",
            withStatus(FOUND).contentType(APPLICATION_JSON).body(families(family("tkf", "LOW")))),
        named("success without a body", withSuccess()),
        named("malformed json", answering("{\"families\": [")));
  }

  static Stream<Named<ResponseCreator>> failingThisPersonOnly() {
    return Stream.of(
        named("refused personal code", withStatus(BAD_REQUEST)),
        named(
            "unknown level",
            answering(families(family("tkf", "LOW"), family("third-pillar", "EXTREME")))),
        named("no families", answering("{}")),
        named("null families", answering("{\"families\": null}")),
        named("null family", answering("{\"families\": [null]}")),
        named(
            "no effective level",
            answering("{\"families\": [{\"family\": \"tkf\", \"computed\": \"HIGH\"}]}")),
        named(
            "null effective level",
            answering("{\"families\": [{\"family\": \"tkf\", \"effective\": null}]}")));
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = {"", "   "})
  void unavailableWithoutSendingARequestWhenNoApiTokenIsConfigured(@Nullable String apiToken) {
    assertThatThrownBy(() -> effectiveRiskLevels(apiToken).isHighRisk(PERSONAL_CODE))
        .isInstanceOf(RiskLevelUnavailableException.class);
    server.verify();
  }

  @ParameterizedTest
  @MethodSource("failingEveryone")
  void asksNoOneElseDuringTheCooldownAfterAmlServiceItselfFailed(ResponseCreator failingEveryone) {
    server.expect(once(), requestTo(EFFECTIVE_URL)).andRespond(failingEveryone);
    var effectiveRiskLevels = effectiveRiskLevels(API_TOKEN);
    assertThatThrownBy(() -> effectiveRiskLevels.isHighRisk(PERSONAL_CODE))
        .isInstanceOf(RiskLevelUnavailableException.class);

    clock.tick(29, SECONDS);

    assertThatThrownBy(() -> effectiveRiskLevels.isHighRisk(OTHER_PERSONAL_CODE))
        .isInstanceOf(RiskLevelUnavailableException.class)
        .hasNoCause();
    server.verify();
  }

  @ParameterizedTest
  @MethodSource("failingThisPersonOnly")
  void asksForTheNextPersonAtOnceWhenOnlyThisPersonsAnswerFailed(
      ResponseCreator failingThisPerson) {
    server.expect(once(), requestTo(EFFECTIVE_URL)).andRespond(failingThisPerson);
    server
        .expect(once(), requestTo(OTHER_EFFECTIVE_URL))
        .andRespond(withSuccess(families(family("tkf", "HIGH")), APPLICATION_JSON));
    var effectiveRiskLevels = effectiveRiskLevels(API_TOKEN);
    assertThatThrownBy(() -> effectiveRiskLevels.isHighRisk(PERSONAL_CODE))
        .isInstanceOf(RiskLevelUnavailableException.class);

    assertThat(effectiveRiskLevels.isHighRisk(OTHER_PERSONAL_CODE)).isTrue();
    server.verify();
  }

  @Test
  @Timeout(value = 30, unit = TimeUnit.SECONDS, threadMode = ThreadMode.SEPARATE_THREAD)
  void asksNoOneElseDuringTheCooldownAfterABodyStalledPastTheReadTimeout() throws IOException {
    var requestedPaths = new CopyOnWriteArrayList<String>();
    var stallingAmlService = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    stallingAmlService.createContext(
        "/",
        exchange -> {
          requestedPaths.add(exchange.getRequestURI().getPath());
          exchange.getResponseHeaders().set("Content-Type", "application/json");
          exchange.sendResponseHeaders(200, 1000);
          exchange.getResponseBody().write("{\"families\": [".getBytes(UTF_8));
          exchange.getResponseBody().flush();
          sleepThroughTheReadTimeout();
          exchange.close();
        });
    var handlers = Executors.newCachedThreadPool(Thread.ofPlatform().daemon().factory());
    stallingAmlService.setExecutor(handlers);
    stallingAmlService.start();
    try {
      var properties =
          new AmlServiceProperties(
              true,
              "http://127.0.0.1:" + stallingAmlService.getAddress().getPort(),
              API_TOKEN,
              ofSeconds(5),
              ofSeconds(60),
              true,
              ofSeconds(1),
              ofSeconds(1),
              ofSeconds(1),
              ofSeconds(5));
      var effectiveRiskLevels =
          new EffectiveRiskLevels(
              new EffectiveRiskLevelClient(
                  EffectiveRiskLevelConfiguration.effectiveRiskLevelRestClient(properties),
                  properties),
              clock);
      var askedAt = System.nanoTime();
      assertThatThrownBy(() -> effectiveRiskLevels.isHighRisk(PERSONAL_CODE))
          .isInstanceOf(RiskLevelUnavailableException.class);
      assertThat(Duration.ofNanos(System.nanoTime() - askedAt)).isLessThan(STALL.dividedBy(2));

      assertThatThrownBy(() -> effectiveRiskLevels.isHighRisk(OTHER_PERSONAL_CODE))
          .isInstanceOf(RiskLevelUnavailableException.class);
      assertThat(requestedPaths).doesNotContain("/v1/risk-levels/effective/" + OTHER_PERSONAL_CODE);
    } finally {
      stallingAmlService.stop(0);
      handlers.shutdownNow();
    }
  }

  private static void sleepThroughTheReadTimeout() {
    try {
      Thread.sleep(STALL.toMillis());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  @Test
  void asksAgainOnceTheCooldownHasPassed() {
    server.expect(once(), requestTo(EFFECTIVE_URL)).andRespond(withServerError());
    server
        .expect(once(), requestTo(OTHER_EFFECTIVE_URL))
        .andRespond(withSuccess(families(family("tkf", "HIGH")), APPLICATION_JSON));
    var effectiveRiskLevels = effectiveRiskLevels(API_TOKEN);
    assertThatThrownBy(() -> effectiveRiskLevels.isHighRisk(PERSONAL_CODE))
        .isInstanceOf(RiskLevelUnavailableException.class);

    clock.tick(30, SECONDS);

    assertThat(effectiveRiskLevels.isHighRisk(OTHER_PERSONAL_CODE)).isTrue();
    server.verify();
  }

  @Test
  void asksForTheCompanysEffectiveLevelWithTheBearerToken() {
    server
        .expect(once(), requestTo(COMPANY_EFFECTIVE_URL))
        .andExpect(method(GET))
        .andExpect(header(AUTHORIZATION, "Bearer " + API_TOKEN))
        .andExpect(header(ACCEPT, APPLICATION_JSON_VALUE))
        .andRespond(withSuccess(families(family("company", "LOW")), APPLICATION_JSON));

    effectiveRiskLevels(API_TOKEN).isHighRiskCompany(REGISTRY_CODE);

    server.verify();
  }

  @Test
  void highRiskCompanyWhenItsCompanyFamilyIsEffectivelyHigh() {
    respondForTheCompany(
        families(
            """
            {"family": "company", "computed": "MEDIUM", "effective": "HIGH",
             "override": {"outcome": "OVERRIDDEN", "level": "HIGH",
                          "createdAt": "2026-11-02T10:00:00Z"}}
            """));

    assertThat(effectiveRiskLevels(API_TOKEN).isHighRiskCompany(REGISTRY_CODE)).isTrue();
  }

  @Test
  void notHighRiskCompanyWhenTheSpecialistOverrodeAComputedHighToMedium() {
    respondForTheCompany(
        families(
            """
            {"family": "company", "computed": "HIGH", "effective": "MEDIUM",
             "override": {"outcome": "OVERRIDDEN", "level": "MEDIUM",
                          "createdAt": "2026-11-02T10:00:00Z"}}
            """));

    assertThat(effectiveRiskLevels(API_TOKEN).isHighRiskCompany(REGISTRY_CODE)).isFalse();
  }

  @Test
  void companyRiskLevelUnavailableWhenAmlServiceHasNoLevelForIt() {
    respondForTheCompany(families());

    assertThatThrownBy(() -> effectiveRiskLevels(API_TOKEN).isHighRiskCompany(REGISTRY_CODE))
        .isInstanceOf(RiskLevelUnavailableException.class)
        .hasNoCause();
  }

  @Test
  void asksForTheNextCompanyAndPersonAtOnceWhenAmlServiceHadNoLevelForThisCompany() {
    respondForTheCompany(families());
    server
        .expect(once(), requestTo(OTHER_COMPANY_EFFECTIVE_URL))
        .andRespond(withSuccess(families(family("company", "HIGH")), APPLICATION_JSON));
    server
        .expect(once(), requestTo(EFFECTIVE_URL))
        .andRespond(withSuccess(families(family("tkf", "HIGH")), APPLICATION_JSON));
    var effectiveRiskLevels = effectiveRiskLevels(API_TOKEN);
    assertThatThrownBy(() -> effectiveRiskLevels.isHighRiskCompany(REGISTRY_CODE))
        .isInstanceOf(RiskLevelUnavailableException.class);

    assertThat(effectiveRiskLevels.isHighRiskCompany(OTHER_REGISTRY_CODE)).isTrue();
    assertThat(effectiveRiskLevels.isHighRisk(PERSONAL_CODE)).isTrue();
    server.verify();
  }

  @Test
  void highRiskCompanyWhenItsCompanyFamilyIsHighEvenIfAnotherEntryIsUnusable() {
    respondForTheCompany(families(family("company", "EXTREME"), family("company", "HIGH")));

    assertThat(effectiveRiskLevels(API_TOKEN).isHighRiskCompany(REGISTRY_CODE)).isTrue();
  }

  @ParameterizedTest
  @MethodSource("unanswered")
  void companyRiskLevelUnavailableWithoutCarryingTheHttpFailureWhenAmlServiceCannotAnswer(
      ResponseCreator unanswered) {
    server.expect(once(), requestTo(COMPANY_EFFECTIVE_URL)).andRespond(unanswered);

    assertThatThrownBy(() -> effectiveRiskLevels(API_TOKEN).isHighRiskCompany(REGISTRY_CODE))
        .isInstanceOf(RiskLevelUnavailableException.class)
        .hasNoCause();
    server.verify();
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = {"", "   "})
  void companyRiskLevelUnavailableWithoutSendingARequestWhenNoApiTokenIsConfigured(
      @Nullable String apiToken) {
    assertThatThrownBy(() -> effectiveRiskLevels(apiToken).isHighRiskCompany(REGISTRY_CODE))
        .isInstanceOf(RiskLevelUnavailableException.class);
    server.verify();
  }

  @ParameterizedTest
  @MethodSource("unanswered")
  void asksForTheNextCompanyAndPersonAtOnceWhateverFailedForACompany(ResponseCreator unanswered) {
    server.expect(once(), requestTo(COMPANY_EFFECTIVE_URL)).andRespond(unanswered);
    server
        .expect(once(), requestTo(OTHER_COMPANY_EFFECTIVE_URL))
        .andRespond(withSuccess(families(family("company", "HIGH")), APPLICATION_JSON));
    server
        .expect(once(), requestTo(EFFECTIVE_URL))
        .andRespond(withSuccess(families(family("tkf", "HIGH")), APPLICATION_JSON));
    var effectiveRiskLevels = effectiveRiskLevels(API_TOKEN);
    assertThatThrownBy(() -> effectiveRiskLevels.isHighRiskCompany(REGISTRY_CODE))
        .isInstanceOf(RiskLevelUnavailableException.class);

    assertThat(effectiveRiskLevels.isHighRiskCompany(OTHER_REGISTRY_CODE)).isTrue();
    assertThat(effectiveRiskLevels.isHighRisk(PERSONAL_CODE)).isTrue();
    server.verify();
  }

  @Test
  void asksForNoCompanyDuringTheCooldownAfterAmlServiceFailedForAPerson() {
    server.expect(once(), requestTo(EFFECTIVE_URL)).andRespond(withServerError());
    var effectiveRiskLevels = effectiveRiskLevels(API_TOKEN);
    assertThatThrownBy(() -> effectiveRiskLevels.isHighRisk(PERSONAL_CODE))
        .isInstanceOf(RiskLevelUnavailableException.class);

    clock.tick(29, SECONDS);

    assertThatThrownBy(() -> effectiveRiskLevels.isHighRiskCompany(REGISTRY_CODE))
        .isInstanceOf(RiskLevelUnavailableException.class)
        .hasNoCause();
    server.verify();
  }

  @Test
  void asksForTheCompanyOnceTheCooldownHasPassed() {
    server.expect(once(), requestTo(EFFECTIVE_URL)).andRespond(withServerError());
    server
        .expect(once(), requestTo(COMPANY_EFFECTIVE_URL))
        .andRespond(withSuccess(families(family("company", "HIGH")), APPLICATION_JSON));
    var effectiveRiskLevels = effectiveRiskLevels(API_TOKEN);
    assertThatThrownBy(() -> effectiveRiskLevels.isHighRisk(PERSONAL_CODE))
        .isInstanceOf(RiskLevelUnavailableException.class);

    clock.tick(30, SECONDS);

    assertThat(effectiveRiskLevels.isHighRiskCompany(REGISTRY_CODE)).isTrue();
    server.verify();
  }

  private static ResponseCreator answering(String body) {
    return withSuccess(body, APPLICATION_JSON);
  }

  private void respond(String body) {
    server.expect(once(), requestTo(EFFECTIVE_URL)).andRespond(answering(body));
  }

  private void respondForTheCompany(String body) {
    server.expect(once(), requestTo(COMPANY_EFFECTIVE_URL)).andRespond(answering(body));
  }

  private static String families(String... families) {
    return "{\"families\": [" + String.join(", ", families) + "]}";
  }

  private static String family(String family, String effective) {
    return """
        {"family": "%s", "computed": "%s", "effective": "%s", "override": null}
        """
        .formatted(family, effective, effective);
  }

  private EffectiveRiskLevels effectiveRiskLevels(@Nullable String apiToken) {
    return new EffectiveRiskLevels(
        new EffectiveRiskLevelClient(
            restClient,
            new AmlServiceProperties(
                true,
                BASE_URL,
                apiToken,
                ofSeconds(5),
                ofSeconds(60),
                true,
                ofSeconds(1),
                ofSeconds(2),
                ofSeconds(1),
                ofSeconds(5))),
        clock);
  }
}
