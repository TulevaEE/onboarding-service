package ee.tuleva.onboarding.epis;

import static java.util.Arrays.asList;
import static org.springframework.http.HttpMethod.GET;

import ee.tuleva.onboarding.auth.jwt.JwtTokenUtil;
import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.contribution.Contribution;
import ee.tuleva.onboarding.epis.account.FundBalanceDto;
import ee.tuleva.onboarding.epis.application.ApplicationResponse;
import ee.tuleva.onboarding.epis.cashflows.CashFlowStatement;
import ee.tuleva.onboarding.epis.contact.ContactDetails;
import ee.tuleva.onboarding.epis.fund.FundDto;
import ee.tuleva.onboarding.epis.fund.NavDto;
import ee.tuleva.onboarding.epis.mandate.ApplicationDTO;
import ee.tuleva.onboarding.epis.mandate.ApplicationResponseDTO;
import ee.tuleva.onboarding.epis.mandate.MandateDto;
import ee.tuleva.onboarding.epis.mandate.command.MandateCommand;
import ee.tuleva.onboarding.epis.mandate.command.MandateCommandResponse;
import ee.tuleva.onboarding.epis.payment.rate.PaymentRateDto;
import ee.tuleva.onboarding.epis.withdrawals.ArrestsBankruptciesDto;
import ee.tuleva.onboarding.epis.withdrawals.FundPensionCalculationDto;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldNameConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Service
@Slf4j
@RequiredArgsConstructor
@Profile("!mock")
public class EpisService {
  private final RestTemplate restTemplate;
  private final JwtTokenUtil jwtTokenUtil;
  private final CacheManager cacheManager;

  @Value("${epis.service.url}")
  String episServiceUrl;

  @Cacheable(
      value = EpisCacheIdentifier.Fields.APPLICATIONS_CACHE,
      key = "#person.personalCode",
      sync = true)
  public List<ApplicationDTO> getApplications(Person person) {
    String url = episServiceUrl + "/applications";

    log.info("Getting applications from {} for {}", url, person.getPersonalCode());

    ResponseEntity<ApplicationDTO[]> response =
        restTemplate.exchange(url, GET, getHeadersEntity(), ApplicationDTO[].class);

    return asList(response.getBody());
  }

  @Cacheable(
      value = EpisCacheIdentifier.Fields.CASH_FLOW_STATEMENT_CACHE,
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
    return restTemplate.exchange(url, GET, getHeadersEntity(), CashFlowStatement.class).getBody();
  }

  public void clearCache(Person person) {
    log.info("Clearing cache for {}", person.getPersonalCode());
    Arrays.stream(EpisCacheIdentifier.values())
        .forEach(
            cache -> {
              if (cache.shouldEvict()) {
                cacheManager.getCache(cache.name()).evict(person.getPersonalCode());
              }
            });
  }

  @Cacheable(
      value = EpisCacheIdentifier.Fields.CONTACT_DETAILS_CACHE,
      key = "#person.personalCode",
      sync = true)
  public ContactDetails getContactDetails(Person person) {
    return getContactDetails(person, userJwtToken());
  }

  @Cacheable(
      value = EpisCacheIdentifier.Fields.CONTACT_DETAILS_CACHE,
      key = "#person.personalCode",
      sync = true)
  public ContactDetails getContactDetails(Person person, String jwtToken) {
    String url = episServiceUrl + "/contact-details";

    log.info("Getting contact details from {} for {}", url, person.getPersonalCode());

    ResponseEntity<ContactDetails> response =
        restTemplate.exchange(url, GET, getHeadersEntity(jwtToken), ContactDetails.class);

    return response.getBody();
  }

  @Cacheable(
      value = EpisCacheIdentifier.Fields.ACCOUNT_STATEMENT_CACHE,
      key = "#person.personalCode",
      sync = true)
  public List<FundBalanceDto> getAccountStatement(Person person) {
    String url = episServiceUrl + "/account-statement";

    log.info("Getting account statement from {} for {}", url, person.getPersonalCode());

    ResponseEntity<FundBalanceDto[]> response =
        restTemplate.exchange(url, GET, getHeadersEntity(), FundBalanceDto[].class);

    return asList(response.getBody());
  }

  @Cacheable(
      value = EpisCacheIdentifier.Fields.CONTRIBUTIONS_CACHE,
      key = "#person.personalCode",
      sync = true)
  public List<Contribution> getContributions(Person person) {
    String url = episServiceUrl + "/contributions";

    log.info("Getting contributions for {}", person.getPersonalCode());

    ResponseEntity<Contribution[]> response =
        restTemplate.exchange(url, GET, getHeadersEntity(), Contribution[].class);

    return asList(response.getBody());
  }

  @Cacheable(value = EpisCacheIdentifier.Fields.FUNDS_CACHE, unless = "#result.isEmpty()")
  public List<FundDto> getFunds() {
    String url = episServiceUrl + "/funds";

    log.info("Getting funds from {}", url);

    ResponseEntity<FundDto[]> response =
        restTemplate.exchange(url, GET, getHeadersEntity(), FundDto[].class);

    return asList(response.getBody());
  }

  @Cacheable(
      value = EpisCacheIdentifier.Fields.FUND_PENSION_CALCULATION_CACHE,
      key = "#person.personalCode",
      sync = true)
  public FundPensionCalculationDto getFundPensionCalculation(Person person) {
    String url = episServiceUrl + "/fund-pension-calculation";

    log.info("Getting fund pension calculation for {}", person.getPersonalCode());

    ResponseEntity<FundPensionCalculationDto> response =
        restTemplate.exchange(url, GET, getHeadersEntity(), FundPensionCalculationDto.class);

    return response.getBody();
  }

  @Cacheable(
      value = EpisCacheIdentifier.Fields.ARRESTS_BANKRUPTCIES_CACHE,
      key = "#person.personalCode",
      sync = true)
  public ArrestsBankruptciesDto getArrestsBankruptciesPresent(Person person) {
    String url = episServiceUrl + "/arrests-bankruptcies";

    log.info("Getting arrests/bankruptcies information for {}", person.getPersonalCode());

    ResponseEntity<ArrestsBankruptciesDto> response =
        restTemplate.exchange(url, GET, getHeadersEntity(), ArrestsBankruptciesDto.class);

    return response.getBody();
  }

  public NavDto getNav(String isin, LocalDate date) {
    log.info("Fetching NAV for fund from EPIS service: isin={}, date={}", isin, date);
    String url = episServiceUrl + "/navs/" + isin + "?date=" + date;
    return restTemplate
        .exchange(url, GET, new HttpEntity<>(getHeaders(serviceJwtToken())), NavDto.class)
        .getBody();
  }

  public MandateCommandResponse sendMandateV2(MandateCommand<?> mandate) {
    String url = episServiceUrl + "/mandates-v2";

    return restTemplate.postForObject(
        url, new HttpEntity<>(mandate, getUserHeaders()), MandateCommandResponse.class);
  }

  public ApplicationResponseDTO sendMandate(MandateDto mandate) {
    String url = episServiceUrl + "/mandates";

    return restTemplate.postForObject(
        url, new HttpEntity<>(mandate, getUserHeaders()), ApplicationResponseDTO.class);
  }

  public ApplicationResponse sendPaymentRateApplication(PaymentRateDto paymentRateDto) {
    String url = episServiceUrl + "/payment-rate";

    return restTemplate.postForObject(
        url, new HttpEntity<>(paymentRateDto, getUserHeaders()), ApplicationResponse.class);
  }

  @CacheEvict(
      value = EpisCacheIdentifier.Fields.CONTACT_DETAILS_CACHE,
      key = "#person.personalCode")
  public ContactDetails updateContactDetails(Person person, ContactDetails contactDetails) {
    String url = episServiceUrl + "/contact-details";

    log.info("Updating contact details for {}", contactDetails.getPersonalCode());

    return restTemplate.postForObject(
        url, new HttpEntity<>(contactDetails, getUserHeaders()), ContactDetails.class);
  }

  private HttpEntity<String> getHeadersEntity() {
    return getHeadersEntity(userJwtToken());
  }

  private HttpEntity<String> getHeadersEntity(String jwtToken) {
    return new HttpEntity<>(getHeaders(jwtToken));
  }

  private HttpHeaders getUserHeaders() {
    return getHeaders(userJwtToken());
  }

  private HttpHeaders getHeaders(String jwtToken) {
    HttpHeaders headers = createJsonHeaders();
    headers.add("Authorization", "Bearer " + jwtToken);
    return headers;
  }

  private HttpHeaders createJsonHeaders() {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    return headers;
  }

  private String userJwtToken() {
    final var authentication =
        SecurityContextHolder.getContext().getAuthentication(); // Use Authentication Holder
    if (authentication != null) {
      return (String) authentication.getCredentials();
    }
    throw new IllegalStateException("No authentication present!");
  }

  private String serviceJwtToken() {
    return jwtTokenUtil.generateServiceToken();
  }

  @FieldNameConstants(onlyExplicitlyIncluded = true)
  private enum EpisCacheIdentifier {
    @FieldNameConstants.Include
    APPLICATIONS_CACHE(),
    @FieldNameConstants.Include
    CONTACT_DETAILS_CACHE(),
    @FieldNameConstants.Include
    ACCOUNT_STATEMENT_CACHE(),
    @FieldNameConstants.Include
    CASH_FLOW_STATEMENT_CACHE(),
    @FieldNameConstants.Include
    FUNDS_CACHE(),
    @FieldNameConstants.Include
    CONTRIBUTIONS_CACHE(),
    @FieldNameConstants.Include
    FUND_PENSION_CALCULATION_CACHE(),
    @FieldNameConstants.Include
    ARRESTS_BANKRUPTCIES_CACHE();

    public boolean shouldEvict() {
      return this != FUNDS_CACHE;
    }
  }
}
