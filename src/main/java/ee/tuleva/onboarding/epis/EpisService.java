package ee.tuleva.onboarding.epis;

import ee.tuleva.onboarding.auth.jwt.JwtTokenUtil;
import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.contribution.Contribution;
import ee.tuleva.onboarding.epis.account.FundBalanceDto;
import ee.tuleva.onboarding.epis.application.ApplicationResponse;
import ee.tuleva.onboarding.epis.cancellation.CancellationDto;
import ee.tuleva.onboarding.epis.cashflows.CashFlowStatement;
import ee.tuleva.onboarding.epis.contact.ContactDetails;
import ee.tuleva.onboarding.epis.fund.FundDto;
import ee.tuleva.onboarding.epis.fund.NavDto;
import ee.tuleva.onboarding.epis.mandate.ApplicationDTO;
import ee.tuleva.onboarding.epis.mandate.ApplicationResponseDTO;
import ee.tuleva.onboarding.epis.mandate.MandateDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.context.annotation.Profile;
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

import java.time.LocalDate;
import java.util.List;

import static java.util.Arrays.asList;
import static org.springframework.http.HttpMethod.GET;

@Service
@Slf4j
@RequiredArgsConstructor
@Profile("!mock")
public class EpisService {

  private final String APPLICATIONS_CACHE_NAME = "applications";
  private final String TRANSFER_APPLICATIONS_CACHE_NAME = "transferApplications";
  private final String CONTACT_DETAILS_CACHE_NAME = "contactDetails";
  private final String ACCOUNT_STATEMENT_CACHE_NAME = "accountStatement";
  private final String CASH_FLOW_STATEMENT_CACHE_NAME = "cashFlowStatement";
  private final String FUNDS_CACHE_NAME = "funds";
  private final String CONTRIBUTIONS_CACHE_NAME = "contributions";

  private final RestOperations userTokenRestTemplate;
  private final OAuth2RestOperations clientCredentialsRestTemplate;
  private final JwtTokenUtil jwtTokenUtil;

  @Value("${epis.service.url}")
  String episServiceUrl;

  @Cacheable(value = APPLICATIONS_CACHE_NAME, key = "#person.personalCode", sync = true)
  public List<ApplicationDTO> getApplications(Person person) {
    String url = episServiceUrl + "/applications";

    log.info("Getting applications from {} for {}", url, person.getPersonalCode());

    ResponseEntity<ApplicationDTO[]> response =
        userTokenRestTemplate.exchange(url, GET, getHeadersEntity(), ApplicationDTO[].class);

    return asList(response.getBody());
  }

  @Cacheable(
      value = CASH_FLOW_STATEMENT_CACHE_NAME,
      key = "{ #person.personalCode, #fromDate, #toDate }",
      sync = true)
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
  public ContactDetails getContactDetails(Person person) {
    return getContactDetails(person, getToken(), jwtToken());
  }

  @Cacheable(value = CONTACT_DETAILS_CACHE_NAME, key = "#person.personalCode")
  public ContactDetails getContactDetails(Person person, String token, String jwtToken) {
    String url = episServiceUrl + "/contact-details";

    log.info("Getting contact details from {} for {}", url, person.getPersonalCode());

    ResponseEntity<ContactDetails> response =
        userTokenRestTemplate.exchange(url, GET, getHeadersEntity(token, jwtToken), ContactDetails.class);

    return response.getBody();
  }

  @Cacheable(value = ACCOUNT_STATEMENT_CACHE_NAME, key = "#person.personalCode")
  public List<FundBalanceDto> getAccountStatement(Person person) {
    String url = episServiceUrl + "/account-statement";

    log.info("Getting account statement from {} for {}", url, person.getPersonalCode());

    ResponseEntity<FundBalanceDto[]> response =
        userTokenRestTemplate.exchange(url, GET, getHeadersEntity(), FundBalanceDto[].class);

    return asList(response.getBody());
  }

  @Cacheable(value = CONTRIBUTIONS_CACHE_NAME, key = "#person.personalCode")
  public List<Contribution> getContributions(Person person) {
    String url = episServiceUrl + "/contributions";

    log.info("Getting contributions for {}", person.getPersonalCode());

    ResponseEntity<Contribution[]> response =
        userTokenRestTemplate.exchange(url, GET, getHeadersEntity(), Contribution[].class);

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
    log.info("Fetching NAV for fund from EPIS service: isin={}, date={}", isin, date);
    String url = episServiceUrl + "/navs/" + isin + "?date=" + date;
    return clientCredentialsRestTemplate
        .exchange(url, GET, new HttpEntity<>(getServiceHeaders(jwtToken())), NavDto.class)
        .getBody();
  }

  public ApplicationResponseDTO sendMandate(MandateDto mandate) {
    String url = episServiceUrl + "/mandates";

    return userTokenRestTemplate.postForObject(
        url, new HttpEntity<>(mandate, getUserHeaders()), ApplicationResponseDTO.class);
  }

  public ApplicationResponse sendCancellation(CancellationDto cancellation) {
    String url = episServiceUrl + "/cancellations";

    return userTokenRestTemplate.postForObject(
        url, new HttpEntity<>(cancellation, getUserHeaders()), ApplicationResponse.class);
  }

  @CacheEvict(value = CONTACT_DETAILS_CACHE_NAME, key = "#person.personalCode")
  public ContactDetails updateContactDetails(Person person, ContactDetails contactDetails) {
    String url = episServiceUrl + "/contact-details";

    log.info("Updating contact details for {}", contactDetails.getPersonalCode());

    return userTokenRestTemplate.postForObject(
        url, new HttpEntity<>(contactDetails, getUserHeaders()), ContactDetails.class);
  }

  private HttpEntity<String> getHeadersEntity() {
    return getHeadersEntity(getToken(), jwtToken());
  }

  private HttpEntity<String> getHeadersEntity(String token, String jwtToken) {
    return new HttpEntity<>(getUserHeaders(token, jwtToken));
  }

  private HttpHeaders getUserHeaders() {
    return getUserHeaders(getToken(), jwtToken());
  }

  private HttpHeaders getUserHeaders(String token, String jwtToken) {
    HttpHeaders headers = createJsonHeaders();
    headers.add("Authorization", "Bearer " + token);
    headers.add("X-Authorization", "Bearer " + jwtToken);
    return headers;
  }

  private HttpHeaders getServiceHeaders(String jwtToken) {
    HttpHeaders headers = createJsonHeaders();
    headers.add("X-Authorization", "Bearer " + jwtToken);
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

  private String jwtToken() {
    return jwtTokenUtil.generateServiceToken();
  }
}
