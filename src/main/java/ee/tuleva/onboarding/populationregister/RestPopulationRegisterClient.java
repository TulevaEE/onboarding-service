package ee.tuleva.onboarding.populationregister;

import static org.springframework.http.MediaType.APPLICATION_JSON;

import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Slf4j
@Service
@Profile("!mock")
class RestPopulationRegisterClient implements PopulationRegisterClient {

  private final RestClient restClient;
  private final RetryTemplate retryTemplate;
  private final PopulationRegisterProperties properties;

  RestPopulationRegisterClient(
      RestClient populationRegisterRestClient,
      RetryTemplate populationRegisterRetryTemplate,
      PopulationRegisterProperties properties) {
    this.restClient = populationRegisterRestClient;
    this.retryTemplate = populationRegisterRetryTemplate;
    this.properties = properties;
  }

  @Override
  public PopulationRegisterPerson fetchPerson(String personalCode) {
    log.info("Fetching person from population register: personalCode={}", personalCode);
    var responses = query(PersonQueryRequest.forIdentity(personalCode), personalCode);
    return PersonMapper.toPerson(first(responses, personalCode));
  }

  @Override
  public List<CustodyRight> fetchCustodyRights(String personalCode) {
    log.info("Fetching custody rights from population register: personalCode={}", personalCode);
    var responses = query(PersonQueryRequest.forCustody(personalCode), personalCode);
    return PersonMapper.toCustodyRights(first(responses, personalCode));
  }

  private PersonResponse[] query(PersonQueryRequest request, String personalCode) {
    var messageId = UUID.randomUUID().toString();
    try {
      return retryTemplate.invoke(() -> post(request, personalCode, messageId));
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

  private PersonResponse[] post(PersonQueryRequest request, String personalCode, String messageId) {
    final String REQUEST_REASON = "oigustatud";
    var responses =
        restClient
            .post()
            .uri("/isikud")
            .contentType(APPLICATION_JSON)
            .accept(APPLICATION_JSON)
            .header("X-Road-Client", properties.clientId())
            .header("X-Road-UserId", personalCode)
            .header("X-Road-Id", messageId)
            .header("RR-Request-Reason", REQUEST_REASON)
            .body(request)
            .retrieve()
            .body(PersonResponse[].class);
    return responses == null ? new PersonResponse[0] : responses;
  }

  private static PersonResponse first(PersonResponse[] responses, String personalCode) {
    if (responses.length == 0) {
      throw new PopulationRegisterException(
          "Population register returned no person: personalCode=" + personalCode);
    }
    return responses[0];
  }
}
