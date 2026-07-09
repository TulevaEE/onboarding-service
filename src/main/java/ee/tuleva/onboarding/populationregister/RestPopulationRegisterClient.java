package ee.tuleva.onboarding.populationregister;

import static ee.tuleva.onboarding.populationregister.PopulationRegisterQueryType.CUSTODY;
import static ee.tuleva.onboarding.populationregister.PopulationRegisterQueryType.IDENTITY;
import static org.springframework.http.MediaType.APPLICATION_JSON;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@Slf4j
@Service
@Profile("!mock")
class RestPopulationRegisterClient implements PopulationRegisterClient {

  private static final ParameterizedTypeReference<List<Map<String, Object>>> RAW_RESPONSE =
      new ParameterizedTypeReference<>() {};

  private final RestClient restClient;
  private final RetryTemplate retryTemplate;
  private final PopulationRegisterProperties properties;
  private final PopulationRegisterResponseStore store;
  private final JsonMapper jsonMapper;

  RestPopulationRegisterClient(
      RestClient populationRegisterRestClient,
      RetryTemplate populationRegisterRetryTemplate,
      PopulationRegisterProperties properties,
      PopulationRegisterResponseStore store,
      JsonMapper jsonMapper) {
    this.restClient = populationRegisterRestClient;
    this.retryTemplate = populationRegisterRetryTemplate;
    this.properties = properties;
    this.store = store;
    this.jsonMapper = jsonMapper;
  }

  @Override
  public PopulationRegisterPerson fetchPerson(
      String requesterPersonalCode, String personalCode, Duration maxAge) {
    return query(
        PersonQueryRequest.forIdentity(personalCode),
        requesterPersonalCode,
        personalCode,
        IDENTITY,
        maxAge,
        PersonMapper::toPerson);
  }

  @Override
  public List<CustodyRight> fetchCustodyRights(String requesterPersonalCode, Duration maxAge) {
    return query(
        PersonQueryRequest.forCustody(requesterPersonalCode),
        requesterPersonalCode,
        requesterPersonalCode,
        CUSTODY,
        maxAge,
        PersonMapper::toCustodyRights);
  }

  private <T> T query(
      PersonQueryRequest request,
      String requesterPersonalCode,
      String personalCode,
      PopulationRegisterQueryType queryType,
      Duration maxAge,
      Function<PersonResponse, T> mapper) {
    var reused =
        store
            .findFresh(personalCode, queryType, maxAge)
            .flatMap(stored -> reusable(stored, personalCode, queryType, mapper));
    if (reused.isPresent()) {
      log.info(
          "Reusing stored population register response: personalCode={}, queryType={}, maxAgeSeconds={}",
          personalCode,
          queryType,
          maxAge.toSeconds());
      return reused.get();
    }
    log.info(
        "Fetching from population register: personalCode={}, queryType={}",
        personalCode,
        queryType);
    var messageId = UUID.randomUUID();
    var response = fetch(request, requesterPersonalCode, messageId);
    store.save(personalCode, queryType, messageId, response);
    return mapper.apply(first(response, personalCode));
  }

  private <T> Optional<T> reusable(
      List<Map<String, Object>> stored,
      String personalCode,
      PopulationRegisterQueryType queryType,
      Function<PersonResponse, T> mapper) {
    try {
      return Optional.of(mapper.apply(first(stored, personalCode)));
    } catch (PopulationRegisterException e) {
      log.warn(
          "Discarding unusable stored population register response: personalCode={}, queryType={}",
          personalCode,
          queryType,
          e);
      return Optional.empty();
    }
  }

  private List<Map<String, Object>> fetch(
      PersonQueryRequest request, String requesterPersonalCode, UUID messageId) {
    try {
      return retryTemplate.invoke(() -> post(request, requesterPersonalCode, messageId));
    } catch (HttpClientErrorException e) {
      throw new PopulationRegisterException(
          "Population register rejected the request: status=" + e.getStatusCode(), e);
    } catch (HttpServerErrorException | ResourceAccessException e) {
      throw new PopulationRegisterUnavailable("Population register is unavailable", e);
    } catch (RestClientException e) {
      throw new PopulationRegisterException(
          "Population register returned an unexpected response", e);
    }
  }

  private List<Map<String, Object>> post(
      PersonQueryRequest request, String requesterPersonalCode, UUID messageId) {
    final String REQUEST_REASON = "oigustatud";
    var responses =
        restClient
            .post()
            .uri("/isikud")
            .contentType(APPLICATION_JSON)
            .accept(APPLICATION_JSON)
            .header("X-Road-Client", properties.clientId())
            .header("X-Road-UserId", requesterPersonalCode)
            .header("X-Road-Id", messageId.toString())
            .header("RR-Request-Reason", REQUEST_REASON)
            .body(request)
            .retrieve()
            .body(RAW_RESPONSE);
    return responses == null ? List.of() : responses;
  }

  private PersonResponse first(List<Map<String, Object>> responses, String personalCode) {
    if (responses.isEmpty()) {
      throw new PopulationRegisterException(
          "Population register returned no person: personalCode=" + personalCode);
    }
    return parse(responses.getFirst(), personalCode);
  }

  private PersonResponse parse(Map<String, Object> response, String personalCode) {
    try {
      return jsonMapper.convertValue(response, PersonResponse.class);
    } catch (JacksonException | IllegalArgumentException e) {
      throw new PopulationRegisterException(
          "Population register response could not be parsed: personalCode=" + personalCode, e);
    }
  }
}
