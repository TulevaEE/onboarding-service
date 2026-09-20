package ee.tuleva.onboarding.aml.sanctions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.auth.principal.PersonImpl;
import ee.tuleva.onboarding.country.Countries;
import java.net.SocketTimeoutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.restclient.test.autoconfigure.RestClientTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.ResponseActions;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

@RestClientTest(OpenSanctionsService.class)
@TestPropertySource(
    properties = {"opensanctions.url=https://dummyUrl", "opensanctions.retry-delay=1ms"})
class OpenSanctionsServiceRetryTest {

  private static final String MATCH_URL =
      "https://dummyUrl/match/default?algorithm=logic-v1&threshold=0.8&cutoff=0.7"
          + "&topics=role.pep&topics=role.rca&topics=sanction"
          + "&facets=countries&facets=topics&facets=datasets&facets=gender";

  private static final String EMPTY_MATCH_RESPONSE =
      """
      {"responses": {"30303039816": {"results": [], "query": {}}}}""";

  private final Person person = new PersonImpl("30303039816", "Peeter", "Meeter");

  @Autowired private OpenSanctionsService openSanctionsService;

  @Autowired private MockRestServiceServer server;

  @BeforeEach
  void resetMockServer() {
    server.reset();
  }

  @Test
  void match_retriesOnReadTimeoutAndSucceeds() {
    server
        .expect(times(2), requestTo(MATCH_URL))
        .andExpect(method(HttpMethod.POST))
        .andRespond(
            request -> {
              throw new SocketTimeoutException("Read timed out");
            });
    expectMatchCall().andRespond(withSuccess(EMPTY_MATCH_RESPONSE, MediaType.APPLICATION_JSON));

    MatchResponse response = openSanctionsService.match(person, Countries.of("ee"));

    assertThat(response.results()).isEmpty();
    server.verify();
  }

  @Test
  void match_retriesOn500AndSucceeds() {
    expectMatchCall().andRespond(withServerError());
    expectMatchCall().andRespond(withSuccess(EMPTY_MATCH_RESPONSE, MediaType.APPLICATION_JSON));

    MatchResponse response = openSanctionsService.match(person, Countries.of("ee"));

    assertThat(response.results()).isEmpty();
    server.verify();
  }

  @Test
  void match_exhaustsThreeAttemptsOnPersistentTimeout() {
    server
        .expect(times(3), requestTo(MATCH_URL))
        .andExpect(method(HttpMethod.POST))
        .andRespond(
            request -> {
              throw new SocketTimeoutException("Read timed out");
            });

    assertThatThrownBy(() -> openSanctionsService.match(person, Countries.of("ee")))
        .isInstanceOf(ResourceAccessException.class);
    server.verify();
  }

  @Test
  void match_exhaustsThreeAttemptsOnPersistent500() {
    server
        .expect(times(3), requestTo(MATCH_URL))
        .andExpect(method(HttpMethod.POST))
        .andRespond(withServerError());

    assertThatThrownBy(() -> openSanctionsService.match(person, Countries.of("ee")))
        .isInstanceOf(HttpServerErrorException.class);
    server.verify();
  }

  @Test
  void match_doesNotRetryOn400() {
    expectMatchCall().andRespond(withBadRequest());

    assertThatThrownBy(() -> openSanctionsService.match(person, Countries.of("ee")))
        .isInstanceOf(HttpClientErrorException.class);
    server.verify();
  }

  @Test
  void matchCompany_retriesOnReadTimeoutAndSucceeds() {
    server
        .expect(requestTo(MATCH_URL))
        .andExpect(method(HttpMethod.POST))
        .andRespond(
            request -> {
              throw new SocketTimeoutException("Read timed out");
            });
    expectMatchCall()
        .andRespond(
            withSuccess(
                """
                {"responses": {"12345678": {"results": [], "query": {}}}}""",
                MediaType.APPLICATION_JSON));

    MatchResponse response =
        openSanctionsService.matchCompany(new ScreenedCompany("Test OÜ", "12345678"));

    assertThat(response.results()).isEmpty();
    server.verify();
  }

  private ResponseActions expectMatchCall() {
    return server.expect(requestTo(MATCH_URL)).andExpect(method(HttpMethod.POST));
  }
}
