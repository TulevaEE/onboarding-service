package ee.tuleva.onboarding.epis;

import static java.util.Arrays.asList;
import static org.springframework.http.HttpMethod.GET;

import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.epis.account.FundBalanceDto;
import ee.tuleva.onboarding.epis.cancellation.CancellationDto;
import ee.tuleva.onboarding.epis.cashflows.CashFlowStatement;
import ee.tuleva.onboarding.epis.contact.UserPreferences;
import ee.tuleva.onboarding.epis.fund.FundDto;
import ee.tuleva.onboarding.epis.fund.NavDto;
import ee.tuleva.onboarding.epis.mandate.ApplicationDTO;
import ee.tuleva.onboarding.epis.mandate.ApplicationResponseDTO;
import ee.tuleva.onboarding.epis.mandate.MandateDto;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.OAuth2RestOperations;
import org.springframework.security.oauth2.provider.authentication.OAuth2AuthenticationDetails;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestOperations;
import org.springframework.web.util.UriComponentsBuilder;

@Service
@Slf4j
@RequiredArgsConstructor
public class EpisService {

  private final String APPLICATIONS_CACHE_NAME = "applications";
  private final String TRANSFER_APPLICATIONS_CACHE_NAME = "transferApplications";
  private final String CONTACT_DETAILS_CACHE_NAME = "contactDetails";
  private final String ACCOUNT_STATEMENT_CACHE_NAME = "accountStatement";
  private final String CASH_FLOW_STATEMENT_CACHE_NAME = "cashFlowStatement";
  private final String FUNDS_CACHE_NAME = "funds";

  private final RestOperations userTokenRestTemplate;
  private final OAuth2RestOperations clientCredentialsRestTemplate;

  @Value("${epis.service.url}")
  String episServiceUrl;

  @Cacheable(value = TRANSFER_APPLICATIONS_CACHE_NAME, key = "#person.personalCode")
  @Deprecated
  public List<ApplicationDTO> getTransferApplications(Person person) {
    String url = episServiceUrl + "/exchanges";

    log.info(
        "Getting exchanges from {} for {} {}", url, person.getFirstName(), person.getLastName());

    ResponseEntity<ApplicationDTO[]> response =
        userTokenRestTemplate.exchange(url, GET, getHeadersEntity(), ApplicationDTO[].class);

    return asList(response.getBody());
  }

  @Cacheable(value = APPLICATIONS_CACHE_NAME, key = "#person.personalCode")
  public List<ApplicationDTO> getApplications(Person person) {
    String url = episServiceUrl + "/applications";

    log.info(
        "Getting applications from {} for {} {}", url, person.getFirstName(), person.getLastName());

    ResponseEntity<ApplicationDTO[]> response =
        userTokenRestTemplate.exchange(url, GET, getHeadersEntity(), ApplicationDTO[].class);

    return asList(response.getBody());
  }

  @Cacheable(
      value = CASH_FLOW_STATEMENT_CACHE_NAME,
      key = "{ #person.personalCode, #fromDate, #toDate }")
  public CashFlowStatement getCashFlowStatement(
      Person person, LocalDate fromDate, LocalDate toDate) {
    String url =
        UriComponentsBuilder.fromHttpUrl(episServiceUrl + "/account-cash-flow-statement")
            .queryParam("from-date", fromDate)
            .queryParam("to-date", toDate)
            .build()
            .toUriString();

    log.info("Getting cash flows from {}", url);
    return userTokenRestTemplate
        .exchange(url, GET, getHeadersEntity(), CashFlowStatement.class)
        .getBody();
  }

  @Caching(
      evict = {
        @CacheEvict(value = APPLICATIONS_CACHE_NAME, key = "#person.personalCode"),
        @CacheEvict(value = TRANSFER_APPLICATIONS_CACHE_NAME, key = "#person.personalCode"),
        @CacheEvict(value = CONTACT_DETAILS_CACHE_NAME, key = "#person.personalCode"),
        @CacheEvict(value = ACCOUNT_STATEMENT_CACHE_NAME, key = "#person.personalCode"),
      })
  public void clearCache(Person person) {
    log.info("Clearing cache for {}", person.getPersonalCode());
  }

  @Cacheable(value = CONTACT_DETAILS_CACHE_NAME, key = "#person.personalCode")
  public UserPreferences getContactDetails(Person person) {
    return getContactDetails(person, getToken());
  }

  @Cacheable(value = CONTACT_DETAILS_CACHE_NAME, key = "#person.personalCode")
  public UserPreferences getContactDetails(Person person, String token) {
    String url = episServiceUrl + "/contact-details";

    log.info(
        "Getting contact details from {} for {} {}",
        url,
        person.getFirstName(),
        person.getLastName());

    ResponseEntity<UserPreferences> response =
        userTokenRestTemplate.exchange(url, GET, getHeadersEntity(token), UserPreferences.class);

    return response.getBody();
  }

  @Cacheable(value = ACCOUNT_STATEMENT_CACHE_NAME, key = "#person.personalCode")
  public List<FundBalanceDto> getAccountStatement(Person person) {
    String url = episServiceUrl + "/account-statement";

    log.info(
        "Getting account statement from {} for {} {}",
        url,
        person.getFirstName(),
        person.getLastName());

    ResponseEntity<FundBalanceDto[]> response =
        userTokenRestTemplate.exchange(url, GET, getHeadersEntity(), FundBalanceDto[].class);

    return asList(response.getBody());
  }

  @Cacheable(value = FUNDS_CACHE_NAME, unless = "#result.isEmpty()")
  public List<FundDto> getFunds() {
    String url = episServiceUrl + "/funds";

    log.info("Getting funds from {}", url);

    ResponseEntity<FundDto[]> response =
        userTokenRestTemplate.exchange(url, GET, getHeadersEntity(), FundDto[].class);

    return asList(response.getBody());
  }

  public NavDto getNav(String isin, LocalDate date) {
    String url = episServiceUrl + "/navs/" + isin + "?date=" + date;
    return clientCredentialsRestTemplate
        .exchange(url, GET, new HttpEntity<>(createJsonHeaders()), NavDto.class)
        .getBody();
  }

  public ApplicationResponseDTO sendMandate(MandateDto mandate) {
    String url = episServiceUrl + "/mandates";

    return userTokenRestTemplate.postForObject(
        url, new HttpEntity<>(mandate, getHeaders()), ApplicationResponseDTO.class);
  }

  public ApplicationResponse sendCancellation(CancellationDto cancellation) {
    String url = episServiceUrl + "/cancellations";

    return userTokenRestTemplate.postForObject(
        url, new HttpEntity<>(cancellation, getHeaders()), ApplicationResponse.class);
  }

  @CacheEvict(value = CONTACT_DETAILS_CACHE_NAME, key = "#person.personalCode")
  public UserPreferences updateContactDetails(Person person, UserPreferences contactDetails) {
    String url = episServiceUrl + "/contact-details";

    log.info("Updating contact details for {}", contactDetails.getPersonalCode());

    return userTokenRestTemplate.postForObject(
        url, new HttpEntity<>(contactDetails, getHeaders()), UserPreferences.class);
  }

  private HttpEntity<String> getHeadersEntity() {
    return getHeadersEntity(getToken());
  }

  private HttpEntity<String> getHeadersEntity(String token) {
    return new HttpEntity<>(getHeaders(token));
  }

  private HttpHeaders getHeaders() {
    return getHeaders(getToken());
  }

  private HttpHeaders getHeaders(String token) {
    HttpHeaders headers = createJsonHeaders();
    headers.add("Authorization", "Bearer " + token);
    return headers;
  }

  private HttpHeaders createJsonHeaders() {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    return headers;
  }

  private String getToken() {
    OAuth2AuthenticationDetails details =
        (OAuth2AuthenticationDetails)
            SecurityContextHolder.getContext().getAuthentication().getDetails();

    return details.getTokenValue();
  }
}
