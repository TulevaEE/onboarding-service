package ee.tuleva.onboarding.aml.sanctions;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.auth.principal.PersonImpl;
import ee.tuleva.onboarding.country.Country;
import java.time.LocalDate;
import java.util.List;
import org.json.JSONException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.restclient.test.autoconfigure.RestClientTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.match.MockRestRequestMatchers;

@RestClientTest(OpenSanctionsService.class)
@TestPropertySource(properties = "opensanctions.url=https://dummyUrl")
class OpenSanctionsServiceTest {

  @Autowired private OpenSanctionsService openSanctionsService;

  @Autowired private MockRestServiceServer server;

  @Autowired private ObjectMapper objectMapper;

  private final String baseUrlForMatching =
      "https://dummyUrl/match/default?algorithm=logic-v1&threshold=0.8&cutoff=0.7"
          + "&topics=role.pep&topics=role.rca&topics=sanction"
          + "&facets=countries&facets=topics&facets=datasets&facets=gender";

  @BeforeEach
  void setUp() {
    // given
    server.reset();
  }

  @AfterEach
  void tearDown() {
    // then
    server.verify();
  }

  @Test
  @DisplayName("Should find a match for a person with a valid Estonian address")
  void canFindMatch() throws JsonProcessingException, JSONException {
    // given
    String firstName = "Peeter";
    String lastName = "Meeter";
    String fullName = firstName + " " + lastName;
    LocalDate birthDate = LocalDate.parse("1960-04-08");
    String personalCode = "36004081234";
    Person person = new PersonImpl(personalCode, firstName, lastName);
    String countryCode = "ee";
    Country country = new Country(countryCode);

    String expectedResultsJson =
        String.format(
            """
        [
          {
            "id": "Q123",
            "caption": "%s",
            "schema": "Person",
            "properties": {
              "gender": ["male"],
              "notes": ["Estonian politician"],
              "name": ["%s"],
              "country": ["ee"]
            }
          }
        ]""",
            fullName, fullName);

    String expectedQueryInResponseJson =
        String.format(
            """
        {
          "schema": "Person",
          "properties": {
            "name": ["%s"],
            "birthDate": ["%s"],
            "country": ["ee"]
          }
        }""",
            fullName, birthDate.toString());

    String mockApiResponseJson =
        String.format(
            """
        {
          "responses": {
            "%s": {
              "status": 200,
              "results": %s,
              "query": %s
            }
          }
        }""",
            personalCode, expectedResultsJson, expectedQueryInResponseJson);

    String expectedRequestBodyJson =
        String.format(
            """
        {
            "queries": {
              "%s": {
                "schema": "Person",
                "properties": {
                  "name": ["%s"],
                  "birthDate": ["%s"],
                  "country": ["%s"],
                  "gender": ["male"]
                }
              }
            }
        }""",
            personalCode, fullName, birthDate.toString(), countryCode);

    server
        .expect(requestTo(baseUrlForMatching))
        .andExpect(MockRestRequestMatchers.method(HttpMethod.POST))
        .andExpect(MockRestRequestMatchers.content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(MockRestRequestMatchers.content().json(expectedRequestBodyJson, true))
        .andRespond(withSuccess(mockApiResponseJson, MediaType.APPLICATION_JSON));

    // when
    MatchResponse actualResponse = openSanctionsService.match(person, country);

    // then
    JSONAssert.assertEquals(
        expectedResultsJson,
        objectMapper.writeValueAsString(actualResponse.results()),
        JSONCompareMode.STRICT);
    JSONAssert.assertEquals(
        expectedQueryInResponseJson,
        objectMapper.writeValueAsString(actualResponse.query()),
        JSONCompareMode.STRICT);
  }

  private String buildJsonArrayStringFromList(List<String> list) throws JsonProcessingException {
    return objectMapper.writeValueAsString(list);
  }

  private String buildMockApiResponseJson(
      String personalCode,
      String fullName,
      LocalDate birthDate,
      List<String> countriesInQueryResponse,
      String resultsJson)
      throws JsonProcessingException {
    String queryPropertiesCountriesJson = buildJsonArrayStringFromList(countriesInQueryResponse);
    String queryJsonSegment =
        String.format(
            """
        {
          "schema": "Person",
          "properties": {
            "name": ["%s"],
            "birthDate": ["%s"],
            "country": %s
          }
        }""",
            fullName, birthDate.toString(), queryPropertiesCountriesJson);

    return String.format(
        """
        {
          "responses": {
            "%s": {
              "status": 200,
              "results": %s,
              "query": %s
            }
          }
        }""",
        personalCode, resultsJson, queryJsonSegment);
  }

  private String buildExpectedRequestBodyJson(
      String personalCode,
      String fullName,
      LocalDate birthDate,
      List<String> countriesInRequest,
      String gender)
      throws JsonProcessingException {
    String requestBodyCountriesJson = buildJsonArrayStringFromList(countriesInRequest);
    return String.format(
        """
        {
            "queries": {
              "%s": {
                "schema": "Person",
                "properties": {
                  "name": ["%s"],
                  "birthDate": ["%s"],
                  "country": %s,
                  "gender": ["%s"]
                }
              }
            }
        }""",
        personalCode, fullName, birthDate.toString(), requestBodyCountriesJson, gender);
  }

  @Test
  @DisplayName("Should use default country 'ee' in request when country is null")
  void match_whenCountryIsNull_usesDefaultCountry() throws JsonProcessingException, JSONException {
    // given
    String firstName = "Peeter";
    String lastName = "Meeter";
    String fullName = firstName + " " + lastName;
    LocalDate birthDate = LocalDate.parse("1960-04-08");
    String personalCode = "36004081234";
    Person person = new PersonImpl(personalCode, firstName, lastName);
    Country country = null;

    List<String> countriesForRequest = List.of("ee");
    List<String> countriesForQueryInResponse = List.of("ee");
    String gender = "male";

    String emptyResultsJson = "[]";
    String mockApiResponseJson =
        buildMockApiResponseJson(
            personalCode, fullName, birthDate, countriesForQueryInResponse, emptyResultsJson);
    String expectedRequestBodyJson =
        buildExpectedRequestBodyJson(
            personalCode, fullName, birthDate, countriesForRequest, gender);

    server
        .expect(requestTo(baseUrlForMatching))
        .andExpect(MockRestRequestMatchers.method(HttpMethod.POST))
        .andExpect(MockRestRequestMatchers.content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(MockRestRequestMatchers.content().json(expectedRequestBodyJson, true))
        .andRespond(withSuccess(mockApiResponseJson, MediaType.APPLICATION_JSON));

    // when
    MatchResponse actualResponse = openSanctionsService.match(person, country);

    // then
    assertTrue(actualResponse.results().isEmpty());
    String expectedQueryInResponseJson =
        String.format(
            """
        {
          "schema": "Person",
          "properties": {
            "name": ["%s"],
            "birthDate": ["%s"],
            "country": %s
          }
        }""",
            fullName,
            birthDate.toString(),
            buildJsonArrayStringFromList(countriesForQueryInResponse));
    JSONAssert.assertEquals(
        expectedQueryInResponseJson,
        objectMapper.writeValueAsString(actualResponse.query()),
        JSONCompareMode.STRICT);
  }

  @Test
  @DisplayName("Should use default country 'ee' in request when country code is null")
  void match_whenCountryCodeIsNull_usesDefaultCountry()
      throws JsonProcessingException, JSONException {
    // given
    String firstName = "Peeter";
    String lastName = "Meeter";
    String fullName = firstName + " " + lastName;
    LocalDate birthDate = LocalDate.parse("1960-04-08");
    String personalCode = "36004081234";
    Person person = new PersonImpl(personalCode, firstName, lastName);
    Country country = new Country(null);

    List<String> countriesForRequest = List.of("ee");
    List<String> countriesForQueryInResponse = List.of("ee");
    String gender = "male";

    String emptyResultsJson = "[]";
    String mockApiResponseJson =
        buildMockApiResponseJson(
            personalCode, fullName, birthDate, countriesForQueryInResponse, emptyResultsJson);
    String expectedRequestBodyJson =
        buildExpectedRequestBodyJson(
            personalCode, fullName, birthDate, countriesForRequest, gender);

    server
        .expect(requestTo(baseUrlForMatching))
        .andExpect(MockRestRequestMatchers.method(HttpMethod.POST))
        .andExpect(MockRestRequestMatchers.content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(MockRestRequestMatchers.content().json(expectedRequestBodyJson, true))
        .andRespond(withSuccess(mockApiResponseJson, MediaType.APPLICATION_JSON));

    // when
    MatchResponse actualResponse = openSanctionsService.match(person, country);

    // then
    assertTrue(actualResponse.results().isEmpty());
    String expectedQueryInResponseJson =
        String.format(
            """
        {
          "schema": "Person",
          "properties": {
            "name": ["%s"],
            "birthDate": ["%s"],
            "country": %s
          }
        }""",
            fullName,
            birthDate.toString(),
            buildJsonArrayStringFromList(countriesForQueryInResponse));
    JSONAssert.assertEquals(
        expectedQueryInResponseJson,
        objectMapper.writeValueAsString(actualResponse.query()),
        JSONCompareMode.STRICT);
  }

  @Test
  @DisplayName(
      "Should use both 'ee' and country code in request when country has a different country code")
  void match_whenCountryHasDifferentCountryCode_usesBothCountries()
      throws JsonProcessingException, JSONException {
    // given
    String firstName = "Peeter";
    String lastName = "Meeter";
    String fullName = firstName + " " + lastName;
    LocalDate birthDate = LocalDate.parse("1960-04-08");
    String personalCode = "36004081234";
    Person person = new PersonImpl(personalCode, firstName, lastName);
    Country country = new Country("fi");

    List<String> countriesForRequest = List.of("ee", "fi");
    List<String> countriesForQueryInResponse = List.of("ee", "fi");
    String gender = "male";

    String emptyResultsJson = "[]";
    String mockApiResponseJson =
        buildMockApiResponseJson(
            personalCode, fullName, birthDate, countriesForQueryInResponse, emptyResultsJson);
    String expectedRequestBodyJson =
        buildExpectedRequestBodyJson(
            personalCode, fullName, birthDate, countriesForRequest, gender);

    server
        .expect(requestTo(baseUrlForMatching))
        .andExpect(MockRestRequestMatchers.method(HttpMethod.POST))
        .andExpect(MockRestRequestMatchers.content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(MockRestRequestMatchers.content().json(expectedRequestBodyJson, false))
        .andRespond(withSuccess(mockApiResponseJson, MediaType.APPLICATION_JSON));

    // when
    MatchResponse actualResponse = openSanctionsService.match(person, country);

    // then
    assertTrue(actualResponse.results().isEmpty());
    String expectedQueryInResponseJson =
        String.format(
            """
        {
          "schema": "Person",
          "properties": {
            "name": ["%s"],
            "birthDate": ["%s"],
            "country": %s
          }
        }""",
            fullName,
            birthDate.toString(),
            buildJsonArrayStringFromList(countriesForQueryInResponse));
    JSONAssert.assertEquals(
        expectedQueryInResponseJson,
        objectMapper.writeValueAsString(actualResponse.query()),
        JSONCompareMode.STRICT);
  }

  @Test
  @DisplayName("Should throw JsonProcessingException when API response JSON is malformed")
  void match_whenResponseJsonIsMalformed_throwsJsonProcessingException()
      throws JsonProcessingException {
    // given
    String firstName = "Peeter";
    String lastName = "Meeter";
    String fullName = firstName + " " + lastName;
    LocalDate birthDate = LocalDate.parse("1960-04-08");
    String personalCode = "36004081234";
    Person person = new PersonImpl(personalCode, firstName, lastName);
    Country country = new Country("ee");

    String malformedMockApiResponseJson =
        "{ \"responses\": { \"36004081234\": { \"status\": 200, \"results\": [{\"id\":\"test\"], \"query\": {} } }";

    List<String> countriesForRequest = List.of("ee");
    String gender = "male";
    String expectedRequestBodyJson =
        buildExpectedRequestBodyJson(
            personalCode, fullName, birthDate, countriesForRequest, gender);

    server
        .expect(requestTo(baseUrlForMatching))
        .andExpect(MockRestRequestMatchers.method(HttpMethod.POST))
        .andExpect(MockRestRequestMatchers.content().json(expectedRequestBodyJson, true))
        .andRespond(withSuccess(malformedMockApiResponseJson, MediaType.APPLICATION_JSON));

    // when
    // then
    assertThrows(
        JsonProcessingException.class,
        () -> {
          openSanctionsService.match(person, country);
        });
  }
}
