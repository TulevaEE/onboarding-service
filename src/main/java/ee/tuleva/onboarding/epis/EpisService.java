package ee.tuleva.onboarding.epis;

import static ee.tuleva.onboarding.config.OAuth2RestTemplateConfiguration.CLIENT_CREDENTIALS_REST_TEMPLATE;
import static ee.tuleva.onboarding.config.OAuth2RestTemplateConfiguration.USER_TOKEN_REST_TEMPLATE;
import static java.util.Arrays.asList;

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
import java.time.LocalDate;
import java.util.List;
import javax.annotation.Resource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

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

  @Resource(name = USER_TOKEN_REST_TEMPLATE)
  private final RestTemplate userTokenRestTemplate;

  @Resource(name = CLIENT_CREDENTIALS_REST_TEMPLATE)
  private final RestTemplate clientCredentialsRestTemplate;

  @Value("${epis.service.url}")
  String episServiceUrl;

  @Cacheable(value = APPLICATIONS_CACHE_NAME, key = "#person.personalCode", sync = true)
  public List<ApplicationDTO> getApplications(Person person) {
    String url = episServiceUrl + "/applications";

    log.info("Getting applications from {} for {}", url, person.getPersonalCode());

    ResponseEntity<ApplicationDTO[]> response =
        userTokenRestTemplate.getForEntity(url, ApplicationDTO[].class);

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
    return userTokenRestTemplate.getForEntity(url, CashFlowStatement.class).getBody();
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
    String url = episServiceUrl + "/contact-details";

    log.info("Getting contact details from {} for {}", url, person.getPersonalCode());

    ResponseEntity<ContactDetails> response =
        userTokenRestTemplate.getForEntity(url, ContactDetails.class);

    return response.getBody();
  }

  @Cacheable(value = ACCOUNT_STATEMENT_CACHE_NAME, key = "#person.personalCode")
  public List<FundBalanceDto> getAccountStatement(Person person) {
    String url = episServiceUrl + "/account-statement";

    log.info("Getting account statement from {} for {}", url, person.getPersonalCode());

    ResponseEntity<FundBalanceDto[]> response =
        userTokenRestTemplate.getForEntity(url, FundBalanceDto[].class);

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

    ResponseEntity<FundDto[]> response = userTokenRestTemplate.getForEntity(url, FundDto[].class);

    return asList(response.getBody());
  }

  public NavDto getNav(String isin, LocalDate date) {
    log.info("Fetching NAV for fund from EPIS service: isin={}, date={}", isin, date);
    String url = episServiceUrl + "/navs/" + isin + "?date=" + date;
    return clientCredentialsRestTemplate.getForEntity(url, NavDto.class).getBody();
  }

  public ApplicationResponseDTO sendMandate(MandateDto mandate) {
    String url = episServiceUrl + "/mandates";

    return userTokenRestTemplate.postForObject(
        url, new HttpEntity<>(mandate), ApplicationResponseDTO.class);
  }

  public ApplicationResponse sendCancellation(CancellationDto cancellation) {
    String url = episServiceUrl + "/cancellations";

    return userTokenRestTemplate.postForObject(
        url, new HttpEntity<>(cancellation), ApplicationResponse.class);
  }

  @CacheEvict(value = CONTACT_DETAILS_CACHE_NAME, key = "#person.personalCode")
  public ContactDetails updateContactDetails(Person person, ContactDetails contactDetails) {
    String url = episServiceUrl + "/contact-details";

    log.info("Updating contact details for {}", contactDetails.getPersonalCode());

    return userTokenRestTemplate.postForObject(
        url, new HttpEntity<>(contactDetails), ContactDetails.class);
  }
}
