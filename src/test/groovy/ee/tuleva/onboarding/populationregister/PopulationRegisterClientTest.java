package ee.tuleva.onboarding.populationregister;

import static ee.tuleva.onboarding.populationregister.CustodyRight.Type.PERSONAL;
import static ee.tuleva.onboarding.populationregister.CustodyRight.Type.PROPERTY;
import static ee.tuleva.onboarding.populationregister.PopulationRegisterPerson.Status.ALIVE;
import static ee.tuleva.onboarding.populationregister.PopulationRegisterQueryType.CUSTODY;
import static ee.tuleva.onboarding.populationregister.PopulationRegisterQueryType.IDENTITY;
import static java.time.Duration.ZERO;
import static java.time.Duration.ofMinutes;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@SpringJUnitConfig(classes = PopulationRegisterClientTest.TestConfig.class)
class PopulationRegisterClientTest {

  private static final String BASE_URL = "http://population-register.local/v1";
  private static final String ISIKUD_URL = BASE_URL + "/isikud";
  private static final String CLIENT_ID = "EE/COM/14118923/tuleva-fund-management";
  private static final String PERSONAL_CODE = "48503150000";
  private static final String REQUESTER = "38812121215";
  private static final Duration MAX_AGE = ofMinutes(15);

  @Autowired RestPopulationRegisterClient client;
  @Autowired MockRestServiceServer server;
  @Autowired PopulationRegisterResponseStore store;

  @BeforeEach
  void resetMocks() {
    server.reset();
    reset(store);
    given(store.findFresh(any(), any(), any())).willReturn(Optional.empty());
  }

  @Test
  void fetchPersonMapsIdentityAndSendsMinimisedRequest() {
    server
        .expect(requestTo(ISIKUD_URL))
        .andExpect(method(HttpMethod.POST))
        .andExpect(header("X-Road-Client", CLIENT_ID))
        .andExpect(header("RR-Request-Reason", "oigustatud"))
        .andExpect(jsonPath("$.isikukoodid[0]").value(PERSONAL_CODE))
        .andExpect(jsonPath("$.andmevaljad.isikuandmed").isNotEmpty())
        .andExpect(jsonPath("$.andmevaljad.suhted").isEmpty())
        .andExpect(jsonPath("$.andmevaljad.hooldusoigused").isEmpty())
        .andExpect(jsonPath("$.andmevaljad.dokumendid").isEmpty())
        .andRespond(withSuccess(personResponse(), MediaType.APPLICATION_JSON));

    PopulationRegisterPerson person = client.fetchPerson(REQUESTER, PERSONAL_CODE, MAX_AGE);

    assertThat(person)
        .isEqualTo(
            new PopulationRegisterPerson(
                PERSONAL_CODE,
                "MARI",
                "MAASIKAS",
                LocalDate.of(1985, 3, 15),
                ALIVE,
                "EESTI VABARIIK"));
    assertThat(person.isAlive()).isTrue();
    server.verify();
  }

  @Test
  void identifiesTheRequesterNotTheLookedUpPersonToTheRegister() {
    server
        .expect(requestTo(ISIKUD_URL))
        .andExpect(header("X-Road-UserId", REQUESTER))
        .andExpect(jsonPath("$.isikukoodid[0]").value(PERSONAL_CODE))
        .andRespond(withSuccess(personResponse(), MediaType.APPLICATION_JSON));

    client.fetchPerson(REQUESTER, PERSONAL_CODE, MAX_AGE);

    server.verify();
  }

  @Test
  void fetchCustodyRightsMapsPropertyCustodyAndRequestsCustodyGroup() {
    server
        .expect(requestTo(ISIKUD_URL))
        .andExpect(method(HttpMethod.POST))
        .andExpect(header("X-Road-UserId", PERSONAL_CODE))
        .andExpect(jsonPath("$.andmevaljad.hooldusoigused").isNotEmpty())
        .andExpect(jsonPath("$.andmevaljad.dokumendid").isEmpty())
        .andRespond(withSuccess(custodyResponse(), MediaType.APPLICATION_JSON));

    List<CustodyRight> rights = client.fetchCustodyRights(PERSONAL_CODE, MAX_AGE);

    assertThat(rights)
        .containsExactly(
            new CustodyRight("61509070000", PERSONAL, true, true),
            new CustodyRight("61509070000", PROPERTY, true, true));
    assertThat(rights)
        .filteredOn(CustodyRight::grantsAssetManagement)
        .containsExactly(new CustodyRight("61509070000", PROPERTY, true, true));
    server.verify();
  }

  @Test
  void storesTheRawResponseUnderTheSameMessageIdItSentToTheRegister() {
    server
        .expect(requestTo(ISIKUD_URL))
        .andRespond(withSuccess(personResponse(), MediaType.APPLICATION_JSON));

    client.fetchPerson(REQUESTER, PERSONAL_CODE, MAX_AGE);

    verify(store).save(eq(PERSONAL_CODE), eq(IDENTITY), any(UUID.class), eq(rawPersonResponse()));
    server.verify();
  }

  @Test
  void storesUnmappedFieldsVerbatimSoTheAuditTrailIsComplete() {
    server
        .expect(requestTo(ISIKUD_URL))
        .andRespond(
            withSuccess(
                """
                [
                  {
                    "isikukood": "48503150000",
                    "eesnimi": "MARI",
                    "perekonnanimi": "MAASIKAS",
                    "valjaMeieKaardistusest": "alles"
                  }
                ]
                """,
                MediaType.APPLICATION_JSON));

    client.fetchPerson(REQUESTER, PERSONAL_CODE, MAX_AGE);

    verify(store)
        .save(
            eq(PERSONAL_CODE),
            eq(IDENTITY),
            any(UUID.class),
            eq(
                List.of(
                    Map.of(
                        "isikukood", PERSONAL_CODE,
                        "eesnimi", "MARI",
                        "perekonnanimi", "MAASIKAS",
                        "valjaMeieKaardistusest", "alles"))));
    server.verify();
  }

  @Test
  void reusesAStoredResponseWithoutCallingTheRegister() {
    given(store.findFresh(PERSONAL_CODE, IDENTITY, MAX_AGE))
        .willReturn(Optional.of(rawPersonResponse()));

    PopulationRegisterPerson person = client.fetchPerson(REQUESTER, PERSONAL_CODE, MAX_AGE);

    assertThat(person.firstName()).isEqualTo("MARI");
    assertThat(person.isAlive()).isTrue();
    verify(store, never()).save(any(), any(), any(), any());
    server.verify();
  }

  @Test
  void reusesStoredCustodyResponseWithoutCallingTheRegister() {
    given(store.findFresh(PERSONAL_CODE, CUSTODY, MAX_AGE))
        .willReturn(Optional.of(rawCustodyResponse()));

    List<CustodyRight> rights = client.fetchCustodyRights(PERSONAL_CODE, MAX_AGE);

    assertThat(rights)
        .containsExactly(
            new CustodyRight("61509070000", PERSONAL, true, true),
            new CustodyRight("61509070000", PROPERTY, true, true));
    server.verify();
  }

  @Test
  void refetchesWhenTheStoredResponseIsEmpty() {
    given(store.findFresh(PERSONAL_CODE, IDENTITY, MAX_AGE)).willReturn(Optional.of(List.of()));
    server
        .expect(times(1), requestTo(ISIKUD_URL))
        .andRespond(withSuccess(personResponse(), MediaType.APPLICATION_JSON));

    PopulationRegisterPerson person = client.fetchPerson(REQUESTER, PERSONAL_CODE, MAX_AGE);

    assertThat(person.firstName()).isEqualTo("MARI");
    verify(store).save(eq(PERSONAL_CODE), eq(IDENTITY), any(UUID.class), eq(rawPersonResponse()));
    server.verify();
  }

  @Test
  void refetchesWhenTheStoredResponseIsMissingRequiredFields() {
    given(store.findFresh(PERSONAL_CODE, IDENTITY, MAX_AGE))
        .willReturn(Optional.of(List.of(Map.of("eesnimi", "MARI"))));
    server
        .expect(times(1), requestTo(ISIKUD_URL))
        .andRespond(withSuccess(personResponse(), MediaType.APPLICATION_JSON));

    PopulationRegisterPerson person = client.fetchPerson(REQUESTER, PERSONAL_CODE, MAX_AGE);

    assertThat(person.personalCode()).isEqualTo(PERSONAL_CODE);
    server.verify();
  }

  @Test
  void refetchesWhenTheStoredResponseNoLongerFitsTheCurrentSchema() {
    given(store.findFresh(PERSONAL_CODE, IDENTITY, MAX_AGE))
        .willReturn(
            Optional.of(List.of(Map.of("isikukood", PERSONAL_CODE, "isikuStaatus", "ELUS"))));
    server
        .expect(times(1), requestTo(ISIKUD_URL))
        .andRespond(withSuccess(personResponse(), MediaType.APPLICATION_JSON));

    PopulationRegisterPerson person = client.fetchPerson(REQUESTER, PERSONAL_CODE, MAX_AGE);

    assertThat(person.firstName()).isEqualTo("MARI");
    server.verify();
  }

  @Test
  void translatesAnUnparseableRegisterResponseIntoADomainException() {
    server
        .expect(times(1), requestTo(ISIKUD_URL))
        .andRespond(
            withSuccess(
                """
                [{ "isikukood": "48503150000", "isikuStaatus": "ELUS" }]
                """,
                MediaType.APPLICATION_JSON));

    assertThatThrownBy(() -> client.fetchPerson(REQUESTER, PERSONAL_CODE, MAX_AGE))
        .isInstanceOf(PopulationRegisterException.class)
        .hasCauseInstanceOf(JacksonException.class);
    server.verify();
  }

  @Test
  void storesAnEmptyRegisterResponseForTheAuditTrailAndStillFails() {
    server
        .expect(times(1), requestTo(ISIKUD_URL))
        .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

    assertThatThrownBy(() -> client.fetchPerson(REQUESTER, PERSONAL_CODE, MAX_AGE))
        .isInstanceOf(PopulationRegisterException.class);

    verify(store).save(eq(PERSONAL_CODE), eq(IDENTITY), any(UUID.class), eq(List.of()));
    server.verify();
  }

  @Test
  void alwaysCallsTheRegisterWhenNoStalenessIsAllowed() {
    server
        .expect(times(1), requestTo(ISIKUD_URL))
        .andRespond(withSuccess(personResponse(), MediaType.APPLICATION_JSON));

    client.fetchPerson(REQUESTER, PERSONAL_CODE, ZERO);

    verify(store).findFresh(PERSONAL_CODE, IDENTITY, ZERO);
    server.verify();
  }

  @Test
  void throwsUnavailableWhenServerErrorsPersist() {
    server.expect(times(2), requestTo(ISIKUD_URL)).andRespond(withServerError());

    assertThatThrownBy(() -> client.fetchPerson(REQUESTER, PERSONAL_CODE, MAX_AGE))
        .isInstanceOf(PopulationRegisterUnavailable.class);
    verify(store, never()).save(any(), any(), any(), any());
    server.verify();
  }

  @Test
  void throwsExceptionWithoutRetryOnClientError() {
    server.expect(times(1), requestTo(ISIKUD_URL)).andRespond(withStatus(HttpStatus.FORBIDDEN));

    assertThatThrownBy(() -> client.fetchPerson(REQUESTER, PERSONAL_CODE, MAX_AGE))
        .isInstanceOf(PopulationRegisterException.class)
        .isNotInstanceOf(PopulationRegisterUnavailable.class);
    verify(store, never()).save(any(), any(), any(), any());
    server.verify();
  }

  private static List<Map<String, Object>> rawPersonResponse() {
    return List.of(
        Map.of(
            "isikukood",
            PERSONAL_CODE,
            "eesnimi",
            "MARI",
            "perekonnanimi",
            "MAASIKAS",
            "synniKuupaev",
            "1985-03-15",
            "isikuStaatus",
            Map.of("elemendiKood", "E", "nimetus", "ELUS"),
            "pohiKodakondsus",
            Map.of("riik", Map.of("elemendiKood", "233", "nimetus", "EESTI VABARIIK"))));
  }

  private static List<Map<String, Object>> rawCustodyResponse() {
    return List.of(
        Map.of(
            "isikukood",
            PERSONAL_CODE,
            "eesnimi",
            "MARI",
            "perekonnanimi",
            "MAASIKAS",
            "hooldusoigused",
            List.of(
                custody("H10", "täielik isikuhooldusõigus"),
                custody("H20", "täielik varahooldusõigus"))));
  }

  private static Map<String, Object> custody(String typeCode, String typeName) {
    return Map.of(
        "liik", Map.of("elemendiKood", typeCode, "nimetus", typeName),
        "staatus", Map.of("elemendiKood", "H1", "nimetus", "kehtiv"),
        "teineIsikIsikukood", "61509070000",
        "teineIsikOlek", Map.of("elemendiKood", "E", "nimetus", "ELUS"));
  }

  private static String personResponse() {
    return """
        [
          {
            "isikukood": "48503150000",
            "eesnimi": "MARI",
            "perekonnanimi": "MAASIKAS",
            "synniKuupaev": "1985-03-15",
            "isikuStaatus": { "elemendiKood": "E", "nimetus": "ELUS" },
            "pohiKodakondsus": { "riik": { "elemendiKood": "233", "nimetus": "EESTI VABARIIK" } }
          }
        ]
        """;
  }

  private static String custodyResponse() {
    return """
        [
          {
            "isikukood": "48503150000",
            "eesnimi": "MARI",
            "perekonnanimi": "MAASIKAS",
            "hooldusoigused": [
              {
                "liik": { "elemendiKood": "H10", "nimetus": "täielik isikuhooldusõigus" },
                "staatus": { "elemendiKood": "H1", "nimetus": "kehtiv" },
                "teineIsikIsikukood": "61509070000",
                "teineIsikOlek": { "elemendiKood": "E", "nimetus": "ELUS" }
              },
              {
                "liik": { "elemendiKood": "H20", "nimetus": "täielik varahooldusõigus" },
                "staatus": { "elemendiKood": "H1", "nimetus": "kehtiv" },
                "teineIsikIsikukood": "61509070000",
                "teineIsikOlek": { "elemendiKood": "E", "nimetus": "ELUS" }
              }
            ]
          }
        ]
        """;
  }

  @Configuration
  static class TestConfig {

    @Bean
    RestClient.Builder populationRegisterRestClientBuilder() {
      return RestClient.builder().baseUrl(BASE_URL);
    }

    @Bean
    MockRestServiceServer mockRestServiceServer(RestClient.Builder builder) {
      return MockRestServiceServer.bindTo(builder).build();
    }

    @Bean
    RestClient populationRegisterRestClient(
        RestClient.Builder builder, MockRestServiceServer mockServer) {
      return builder.build();
    }

    @Bean
    RetryTemplate populationRegisterRetryTemplate() {
      var policy =
          RetryPolicy.builder()
              .includes(HttpServerErrorException.class, ResourceAccessException.class)
              .excludes(HttpClientErrorException.class)
              .maxRetries(1)
              .delay(Duration.ofMillis(1))
              .multiplier(1)
              .maxDelay(Duration.ofMillis(1))
              .build();
      return new RetryTemplate(policy);
    }

    @Bean
    PopulationRegisterProperties populationRegisterProperties() {
      return new PopulationRegisterProperties(BASE_URL, CLIENT_ID);
    }

    @Bean
    PopulationRegisterResponseStore populationRegisterResponseStore() {
      return mock(PopulationRegisterResponseStore.class);
    }

    @Bean
    JsonMapper jsonMapper() {
      return JsonMapper.builder().build();
    }

    @Bean
    RestPopulationRegisterClient restPopulationRegisterClient(
        RestClient populationRegisterRestClient,
        RetryTemplate populationRegisterRetryTemplate,
        PopulationRegisterProperties populationRegisterProperties,
        PopulationRegisterResponseStore store,
        JsonMapper jsonMapper) {
      return new RestPopulationRegisterClient(
          populationRegisterRestClient,
          populationRegisterRetryTemplate,
          populationRegisterProperties,
          store,
          jsonMapper);
    }
  }
}
