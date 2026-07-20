package ee.tuleva.onboarding.populationregister;

import static ee.tuleva.onboarding.populationregister.PopulationRegisterQueryType.CUSTODY;
import static ee.tuleva.onboarding.populationregister.PopulationRegisterQueryType.IDENTITY;
import static org.springframework.http.MediaType.APPLICATION_JSON;

import java.time.Clock;
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
  private final Clock clock;

  RestPopulationRegisterClient(
      RestClient populationRegisterRestClient,
      RetryTemplate populationRegisterRetryTemplate,
      PopulationRegisterProperties properties,
      PopulationRegisterResponseStore store,
      JsonMapper jsonMapper,
      Clock clock) {
    this.restClient = populationRegisterRestClient;
    this.retryTemplate = populationRegisterRetryTemplate;
    this.properties = properties;
    this.store = store;
    this.jsonMapper = jsonMapper;
    this.clock = clock;
  }

  @Override
  public PopulationRegisterResult<PopulationRegisterPerson> fetchPerson(
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
  public PopulationRegisterResult<List<CustodyRight>> fetchCustodyRights(
      String requesterPersonalCode, Duration maxAge) {
    return query(
        PersonQueryRequest.forCustody(requesterPersonalCode),
        requesterPersonalCode,
        requesterPersonalCode,
        CUSTODY,
        maxAge,
        PersonMapper::toCustodyRights);
  }

  @Override
  public PopulationRegisterResult<List<Guardian>> fetchCustodyRights(
      String requesterPersonalCode, String subjectPersonalCode, Duration maxAge) {
    // BYPASS the cache for child-subject custody queries. The store keys responses by
    // personalCode + queryType only, so a child's custody record fetched under parent A would
    // otherwise be served to parent B, skipping B's own per-requester X-Road request/audit. Always
    // fetch fresh; never read or write the store for this direction. (maxAge is therefore unused.)
    return queryFresh(
        PersonQueryRequest.forCustody(subjectPersonalCode),
        requesterPersonalCode,
        CUSTODY,
        PersonMapper::toGuardians);
  }

  private <T> PopulationRegisterResult<T> query(
      PersonQueryRequest request,
      String requesterPersonalCode,
      String personalCode,
      PopulationRegisterQueryType queryType,
      Duration maxAge,
      Function<PersonResponse, T> mapper) {
    var reused =
        store
            .findFresh(personalCode, queryType, maxAge)
            .flatMap(stored -> reusable(stored, queryType, mapper));
    if (reused.isPresent()) {
      log.info(
          "Reusing stored population register response: messageId={}, queryType={}, maxAgeSeconds={}",
          reused.get().messageId(),
          queryType,
          maxAge.toSeconds());
      return reused.get();
    }
    var messageId = UUID.randomUUID();
    log.info("Fetching from population register: messageId={}, queryType={}", messageId, queryType);
    var start = clock.instant();
    var response = fetch(request, requesterPersonalCode, messageId);
    log.info(
        "Population register call finished: messageId={}, queryType={}, durationMs={}",
        messageId,
        queryType,
        Duration.between(start, clock.instant()).toMillis());
    store.save(personalCode, queryType, messageId, response);
    return new PopulationRegisterResult<>(mapper.apply(first(response, messageId)), messageId);
  }

  // Like query(), but never touches the store — no cached reuse, no persisted audit row. Used for
  // child-subject custody queries where reuse across requesters would be incorrect (see caller).
  private <T> PopulationRegisterResult<T> queryFresh(
      PersonQueryRequest request,
      String requesterPersonalCode,
      PopulationRegisterQueryType queryType,
      Function<PersonResponse, T> mapper) {
    var messageId = UUID.randomUUID();
    log.info(
        "Fetching from population register (uncached): messageId={}, queryType={}",
        messageId,
        queryType);
    var start = clock.instant();
    var response = fetch(request, requesterPersonalCode, messageId);
    log.info(
        "Population register call finished: messageId={}, queryType={}, durationMs={}",
        messageId,
        queryType,
        Duration.between(start, clock.instant()).toMillis());
    return new PopulationRegisterResult<>(mapper.apply(first(response, messageId)), messageId);
  }

  private <T> Optional<PopulationRegisterResult<T>> reusable(
      StoredResponse stored,
      PopulationRegisterQueryType queryType,
      Function<PersonResponse, T> mapper) {
    try {
      T data = mapper.apply(first(stored.response(), stored.messageId()));
      return Optional.of(new PopulationRegisterResult<>(data, stored.messageId()));
    } catch (PopulationRegisterException e) {
      log.warn(
          "Discarding unusable stored population register response: messageId={}, queryType={}",
          stored.messageId(),
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

  private PersonResponse first(List<Map<String, Object>> responses, UUID messageId) {
    if (responses.isEmpty()) {
      throw new PopulationRegisterException(
          "Population register returned no person: messageId=" + messageId);
    }
    return parse(responses.getFirst(), messageId);
  }

  private PersonResponse parse(Map<String, Object> response, UUID messageId) {
    try {
      return jsonMapper.convertValue(response, PersonResponse.class);
    } catch (JacksonException | IllegalArgumentException e) {
      throw new PopulationRegisterException(
          "Population register response could not be parsed: messageId=" + messageId, e);
    }
  }
}
