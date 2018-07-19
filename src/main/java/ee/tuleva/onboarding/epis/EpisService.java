package ee.tuleva.onboarding.epis;

import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.epis.account.FundBalanceDto;
import ee.tuleva.onboarding.epis.cashflows.CashFlowStatementDto;
import ee.tuleva.onboarding.epis.contact.UserPreferences;
import ee.tuleva.onboarding.epis.mandate.TransferExchangeDTO;
import ee.tuleva.onboarding.mandate.content.MandateXmlMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.http.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.provider.authentication.OAuth2AuthenticationDetails;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.Date;
import java.util.List;

import static java.util.Arrays.asList;

@Service
@Slf4j
@RequiredArgsConstructor
public class EpisService {

  private final String TRANSFER_APPLICATIONS_CACHE_NAME = "transferApplications";
  private final String CONTACT_DETAILS_CACHE_NAME = "contactDetails";
  private final String ACCOUNT_STATEMENT_CACHE_NAME = "accountStatement";
  private final String CASH_FLOW_STATEMENT_CACHE_NAME = "cashFlowStatement";

  private final RestTemplate restTemplate;

  @Value("${epis.service.url}")
  String episServiceUrl;

  public void process(List<MandateXmlMessage> messages) {

    String url = episServiceUrl + "/processing";

    log.info("Submitting process to {} " + url);

    CreateProcessingCommand command = new CreateProcessingCommand(messages);

    try {
      restTemplate.postForObject(url, getProcessingRequest(command), CreateProcessingCommand.class);
    } catch (HttpStatusCodeException e) {
      throw new EpisServiceException("Error processing mandate messages through epis-service", e);
    }
  }

  @Cacheable(value = TRANSFER_APPLICATIONS_CACHE_NAME, key = "#person.personalCode")
  public List<TransferExchangeDTO> getTransferApplications(Person person) {
    String url = episServiceUrl + "/exchanges";

    log.info("Getting exchanges from {} for {} {}",
        url, person.getFirstName(), person.getLastName());

    ResponseEntity<TransferExchangeDTO[]> response = restTemplate.exchange(
        url, HttpMethod.GET, new HttpEntity(getHeaders()), TransferExchangeDTO[].class);

    return asList(response.getBody());
  }

  @Cacheable(value = CASH_FLOW_STATEMENT_CACHE_NAME, key="#person.personalCode")
  public CashFlowStatementDto getCashFlowStatement(Person person, Instant startTime, Instant endTime) {

    SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
    String url = UriComponentsBuilder
            .fromHttpUrl(episServiceUrl + "/account-cash-flow-statement")
            .queryParam("from-date", dateFormat.format(Date.from(startTime)))
            .queryParam("to-date", dateFormat.format(Date.from(endTime)))
            .build()
            .toUriString();

    log.info("Getting cash flows from {}", url);
    return restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(getHeaders()), CashFlowStatementDto.class).getBody();
  }

  @Caching(evict = {
      @CacheEvict(value = TRANSFER_APPLICATIONS_CACHE_NAME, key = "#person.personalCode"),
      @CacheEvict(value = CONTACT_DETAILS_CACHE_NAME, key = "#person.personalCode"),
      @CacheEvict(value = ACCOUNT_STATEMENT_CACHE_NAME, key = "#person.personalCode"),
      @CacheEvict(value = CASH_FLOW_STATEMENT_CACHE_NAME, key = "#person.personalCode")
  })
  public void clearCache(Person person) {
    clearTransferApplicationsCache(person);
    clearContactDetailsCache(person);
    clearAccountStatementCache(person);
    clearCashFlowCache(person);
  }

  @CacheEvict(value = CASH_FLOW_STATEMENT_CACHE_NAME, key = "#person.personalCode")
  public void clearCashFlowCache(Person person) {
    log.info("Clearing cash flows cache for {} {}", person.getFirstName(), person.getLastName());
  }

  @CacheEvict(value = TRANSFER_APPLICATIONS_CACHE_NAME, key = "#person.personalCode")
  public void clearTransferApplicationsCache(Person person) {
    log.info("Clearing exchanges cache for {} {}",
        person.getFirstName(), person.getLastName());
  }

  @Cacheable(value = CONTACT_DETAILS_CACHE_NAME, key = "#person.personalCode")
  public UserPreferences getContactDetails(Person person) {
    String url = episServiceUrl + "/contact-details";

    log.info("Getting contact details from {} for {} {}",
        url, person.getFirstName(), person.getLastName());

    ResponseEntity<UserPreferences> response = restTemplate.exchange(
        url, HttpMethod.GET, new HttpEntity(getHeaders()), UserPreferences.class);

    return response.getBody();
  }

  @CacheEvict(value = CONTACT_DETAILS_CACHE_NAME, key = "#person.personalCode")
  public void clearContactDetailsCache(Person person) {
    log.info("Clearing contact cache for {} {}",
        person.getFirstName(), person.getLastName());
  }

  private HttpEntity getProcessingRequest(CreateProcessingCommand command) {
    return new HttpEntity<>(command, getHeaders());
  }

  private HttpHeaders getHeaders() {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.add("authorization", "Bearer " + getToken());

    return headers;
  }

  private String getToken() {
    OAuth2AuthenticationDetails details =
        (OAuth2AuthenticationDetails) SecurityContextHolder.getContext().getAuthentication().getDetails();

    return details.getTokenValue();
  }

  @Cacheable(value = ACCOUNT_STATEMENT_CACHE_NAME, key = "#person.personalCode")
  public List<FundBalanceDto> getAccountStatement(Person person) {
    String url = episServiceUrl + "/account-statement";

    log.info("Getting account statement from {} for {} {}",
        url, person.getFirstName(), person.getLastName());

    ResponseEntity<FundBalanceDto[]> response = restTemplate.exchange(
        url, HttpMethod.GET, new HttpEntity(getHeaders()), FundBalanceDto[].class);

    return asList(response.getBody());
  }

  @CacheEvict(value = ACCOUNT_STATEMENT_CACHE_NAME, key = "#person.personalCode")
  public void clearAccountStatementCache(Person person) {
    log.info("Clearing account statement cache for {} {}",
        person.getFirstName(), person.getLastName());
  }

}
