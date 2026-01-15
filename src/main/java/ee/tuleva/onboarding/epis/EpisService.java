package ee.tuleva.onboarding.epis;

import static org.springframework.http.HttpMethod.GET;

import ee.tuleva.onboarding.auth.jwt.JwtTokenUtil;
import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.contribution.Contribution;
import ee.tuleva.onboarding.epis.account.FundBalanceDto;
import ee.tuleva.onboarding.epis.cashflows.CashFlowStatement;
import ee.tuleva.onboarding.epis.contact.ContactDetails;
import ee.tuleva.onboarding.epis.fund.FundDto;
import ee.tuleva.onboarding.epis.fund.NavDto;
import ee.tuleva.onboarding.epis.mandate.ApplicationDTO;
import ee.tuleva.onboarding.epis.mandate.ApplicationResponseDTO;
import ee.tuleva.onboarding.epis.mandate.MandateDto;
import ee.tuleva.onboarding.epis.mandate.command.MandateCommand;
import ee.tuleva.onboarding.epis.mandate.command.MandateCommandResponse;
import ee.tuleva.onboarding.epis.transaction.*;
import ee.tuleva.onboarding.epis.withdrawals.ArrestsBankruptciesDto;
import ee.tuleva.onboarding.epis.withdrawals.FundPensionCalculationDto;
import ee.tuleva.onboarding.epis.withdrawals.FundPensionStatusDto;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Nullable;
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
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Service
@Slf4j
@RequiredArgsConstructor
@Profile("!mock")
public class EpisService {

  private final String APPLICATIONS_CACHE_NAME = "applications";
  private final String CONTACT_DETAILS_CACHE_NAME = "contactDetails";
  private final String ACCOUNT_STATEMENT_CACHE_NAME = "accountStatement";
  private final String CASH_FLOW_STATEMENT_CACHE_NAME = "cashFlowStatement";
  private final String FUNDS_CACHE_NAME = "funds";
  private final String CONTRIBUTIONS_CACHE_NAME = "contributions";
  private final String FUND_PENSION_CALCULATION_CACHE_NAME = "fundPensionCalculation";
  private final String FUND_PENSION_STATUS_CACHE_NAME = "fundPensionStatus";
  private final String ARRESTS_BANKRUPTCIES_CACHE_NAME = "arrestsBankruptcies";

  private final RestTemplate episRestTemplate;

  private final JwtTokenUtil jwtTokenUtil;

  @Value("${epis.service.url}")
  @Nullable
  String episServiceUrl;

  @Value("${epis.service.long-request-url}")
  @Nullable
  String episServiceLongRequestUrl;

  @Cacheable(value = APPLICATIONS_CACHE_NAME, key = "#person.personalCode", sync = true)
  public List<ApplicationDTO> getApplications(Person person) {
    String url = episServiceUrl + "/applications";

    log.info("Getting applications from {} for {}", url, person.getPersonalCode());

    ResponseEntity<ApplicationDTO[]> response =
        episRestTemplate.exchange(url, GET, getHeadersEntity(), ApplicationDTO[].class);

    return toList(response.getBody());
  }

  @Cacheable(
      value = CASH_FLOW_STATEMENT_CACHE_NAME,
      key = "{ #person.personalCode, #fromDate, #toDate }",
      sync = true)
  public CashFlowStatement getCashFlowStatement(
      Person person, LocalDate fromDate, LocalDate toDate) {
    String url =
        UriComponentsBuilder.fromUriString(episServiceUrl)
            .path("/account-cash-flow-statement")
            .queryParam("from-date", fromDate)
            .queryParam("to-date", toDate)
            .build()
            .toUriString();

    log.info("Getting cash flows from {}", url);
    return episRestTemplate
        .exchange(url, GET, getHeadersEntity(), CashFlowStatement.class)
        .getBody();
  }

  @Caching(
      evict = {
        @CacheEvict(value = APPLICATIONS_CACHE_NAME, key = "#person.personalCode"),
        @CacheEvict(value = CONTACT_DETAILS_CACHE_NAME, key = "#person.personalCode"),
        @CacheEvict(value = ACCOUNT_STATEMENT_CACHE_NAME, key = "#person.personalCode"),
        @CacheEvict(value = CONTRIBUTIONS_CACHE_NAME, key = "#person.personalCode"),
        @CacheEvict(value = FUND_PENSION_CALCULATION_CACHE_NAME, key = "#person.personalCode"),
        @CacheEvict(value = FUND_PENSION_STATUS_CACHE_NAME, key = "#person.personalCode"),
        @CacheEvict(value = ARRESTS_BANKRUPTCIES_CACHE_NAME, key = "#person.personalCode"),
      })
  public void clearCache(Person person) {
    log.info("Clearing cache for {}", person.getPersonalCode());
  }

  @Cacheable(value = CONTACT_DETAILS_CACHE_NAME, key = "#person.personalCode", sync = true)
  public ContactDetails getContactDetails(Person person) {
    return getContactDetails(person, userJwtToken());
  }

  @Cacheable(value = CONTACT_DETAILS_CACHE_NAME, key = "#person.personalCode", sync = true)
  public ContactDetails getContactDetails(Person person, String jwtToken) {
    String url = episServiceUrl + "/contact-details";

    log.info("Getting contact details from {} for {}", url, person.getPersonalCode());

    ResponseEntity<ContactDetails> response =
        episRestTemplate.exchange(url, GET, getHeadersEntity(jwtToken), ContactDetails.class);

    return response.getBody();
  }

  @Cacheable(
      value = ACCOUNT_STATEMENT_CACHE_NAME,
      key = "#person.personalCode",
      condition = "#fromDate == null && #toDate == null",
      sync = true)
  public List<FundBalanceDto> getAccountStatement(
      Person person, @Nullable LocalDate fromDate, @Nullable LocalDate toDate) {
    String url =
        UriComponentsBuilder.fromUriString(episServiceUrl)
            .path("/account-statement")
            .queryParamIfPresent("from-date", Optional.ofNullable(fromDate))
            .queryParamIfPresent("to-date", Optional.ofNullable(toDate))
            .build()
            .toUriString();

    log.info("Getting account statement from {} for {}", url, person.getPersonalCode());

    ResponseEntity<FundBalanceDto[]> response =
        episRestTemplate.exchange(url, GET, getHeadersEntity(), FundBalanceDto[].class);

    return toList(response.getBody());
  }

  @Cacheable(value = CONTRIBUTIONS_CACHE_NAME, key = "#person.personalCode", sync = true)
  public List<Contribution> getContributions(Person person) {
    String url = episServiceUrl + "/contributions";

    log.info("Getting contributions for {}", person.getPersonalCode());

    ResponseEntity<Contribution[]> response =
        episRestTemplate.exchange(url, GET, getHeadersEntity(), Contribution[].class);

    return toList(response.getBody());
  }

  @Cacheable(value = FUNDS_CACHE_NAME, unless = "#result.isEmpty()")
  public List<FundDto> getFunds() {
    String url = episServiceUrl + "/funds";

    log.info("Getting funds from {}", url);

    ResponseEntity<FundDto[]> response =
        episRestTemplate.exchange(url, GET, getHeadersEntity(), FundDto[].class);

    return toList(response.getBody());
  }

  @Cacheable(value = FUND_PENSION_CALCULATION_CACHE_NAME, key = "#person.personalCode", sync = true)
  public FundPensionCalculationDto getFundPensionCalculation(Person person) {
    String url = episServiceUrl + "/fund-pension-calculation";

    log.info("Getting fund pension calculation for {}", person.getPersonalCode());

    ResponseEntity<FundPensionCalculationDto> response =
        episRestTemplate.exchange(url, GET, getHeadersEntity(), FundPensionCalculationDto.class);

    return response.getBody();
  }

  @Cacheable(value = FUND_PENSION_STATUS_CACHE_NAME, key = "#person.personalCode", sync = true)
  public FundPensionStatusDto getFundPensionStatus(Person person) {
    String url = episServiceUrl + "/fund-pension-status";

    log.info("Getting fund pension status for {}", person.getPersonalCode());

    ResponseEntity<FundPensionStatusDto> response =
        episRestTemplate.exchange(url, GET, getHeadersEntity(), FundPensionStatusDto.class);

    return response.getBody();
  }

  @Cacheable(value = ARRESTS_BANKRUPTCIES_CACHE_NAME, key = "#person.personalCode", sync = true)
  public ArrestsBankruptciesDto getArrestsBankruptciesPresent(Person person) {
    String url = episServiceUrl + "/arrests-bankruptcies";

    log.info("Getting arrests/bankruptcies information for {}", person.getPersonalCode());

    ResponseEntity<ArrestsBankruptciesDto> response =
        episRestTemplate.exchange(url, GET, getHeadersEntity(), ArrestsBankruptciesDto.class);

    return response.getBody();
  }

  public NavDto getNav(String isin, LocalDate date) {
    log.info("Fetching NAV for fund from EPIS service: isin={}, date={}", isin, date);
    String url = episServiceUrl + "/navs/" + isin + "?date=" + date;
    return episRestTemplate
        .exchange(url, GET, new HttpEntity<>(getHeaders(serviceJwtToken())), NavDto.class)
        .getBody();
  }

  public List<ThirdPillarTransactionDto> getTransactions(LocalDate startDate, LocalDate endDate) {
    log.info(
        "Fetching pension transactions from EPIS service: startDate={}, endDate={}",
        startDate,
        endDate);

    String url =
        UriComponentsBuilder.fromUriString(episServiceLongRequestUrl)
            .pathSegment("transactions")
            .queryParam("startDate", startDate)
            .queryParam("endDate", endDate)
            .toUriString();

    log.debug(
        "Calling remote transactions endpoint at URL: {} (using long-request URL to bypass Cloudflare timeout)",
        url);

    ThirdPillarTransactionDto[] responseArray =
        episRestTemplate
            .exchange(
                url,
                GET,
                new HttpEntity<>(getHeaders(serviceJwtToken())),
                ThirdPillarTransactionDto[].class)
            .getBody();

    return toList(responseArray);
  }

  public List<ExchangeTransactionDto> getExchangeTransactions(
      LocalDate startDate,
      Optional<String> securityFrom,
      Optional<String> securityTo,
      boolean pikFlag) {

    log.info(
        "Fetching exchange transactions from EPIS service: startDate={}, securityFrom={}, securityTo={}, pikFlag={}",
        startDate,
        securityFrom.orElse(""),
        securityTo.orElse(""),
        pikFlag);

    UriComponentsBuilder urlBuilder =
        UriComponentsBuilder.fromUriString(episServiceLongRequestUrl)
            .pathSegment("exchange-transactions")
            .queryParam("startDate", startDate)
            .queryParam("pikFlag", pikFlag);

    securityFrom.ifPresent(sf -> urlBuilder.queryParam("securityFrom", sf));
    securityTo.ifPresent(st -> urlBuilder.queryParam("securityTo", st));

    String url = urlBuilder.toUriString();
    log.debug(
        "Calling remote exchange transactions endpoint at URL: {} (using long-request URL to bypass Cloudflare timeout)",
        url);

    ExchangeTransactionDto[] responseArray =
        episRestTemplate
            .exchange(
                url,
                GET,
                new HttpEntity<>(getHeaders(serviceJwtToken())),
                ExchangeTransactionDto[].class)
            .getBody();

    return toList(responseArray);
  }

  public List<FundTransactionDto> getFundTransactions(
      String isin, LocalDate fromDate, LocalDate toDate) {
    log.info(
        "Fetching fund transactions from EPIS service: isin={}, fromDate={}, toDate={}",
        isin,
        fromDate,
        toDate);

    String url =
        UriComponentsBuilder.fromUriString(episServiceLongRequestUrl)
            .pathSegment("fund-transactions")
            .queryParam("isin", isin)
            .queryParam("fromDate", fromDate)
            .queryParam("toDate", toDate)
            .toUriString();

    log.debug(
        "Calling remote fund transactions endpoint at URL: {} (using long-request URL to bypass Cloudflare timeout)",
        url);

    ResponseEntity<FundTransactionDto[]> response =
        episRestTemplate.exchange(
            url, GET, new HttpEntity<>(getHeaders(serviceJwtToken())), FundTransactionDto[].class);

    return toList(response.getBody());
  }

  public List<TransactionFundBalanceDto> getFundBalances(LocalDate requestDate) {
    log.info("Fetching fund balances from EPIS service for date: {}", requestDate);

    String url =
        UriComponentsBuilder.fromUriString(episServiceLongRequestUrl)
            .pathSegment("fund-balances")
            .queryParam("requestDate", requestDate)
            .toUriString();

    log.debug(
        "Calling remote fund balances endpoint at URL: {} (using long-request URL to bypass Cloudflare timeout)",
        url);

    ResponseEntity<TransactionFundBalanceDto[]> response =
        episRestTemplate.exchange(
            url,
            GET,
            new HttpEntity<>(getHeaders(serviceJwtToken())),
            TransactionFundBalanceDto[].class);

    return toList(response.getBody());
  }

  public List<UnitOwnerDto> getUnitOwners() {
    log.info("Fetching unit owners from EPIS service.");

    String url =
        UriComponentsBuilder.fromUriString(episServiceLongRequestUrl)
            .pathSegment("unit-owners")
            .toUriString();

    log.debug(
        "Calling remote unit owners endpoint at URL: {} (using long-request URL to bypass Cloudflare timeout)",
        url);

    ResponseEntity<UnitOwnerDto[]> response =
        episRestTemplate.exchange(
            url, GET, new HttpEntity<>(getHeaders(serviceJwtToken())), UnitOwnerDto[].class);

    return toList(response.getBody());
  }

  public MandateCommandResponse sendMandateV2(MandateCommand<?> mandate) {
    String url = episServiceUrl + "/mandates-v2";

    return episRestTemplate.postForObject(
        url, new HttpEntity<>(mandate, getUserHeaders()), MandateCommandResponse.class);
  }

  public ApplicationResponseDTO sendMandate(MandateDto mandate) {
    String url = episServiceUrl + "/mandates";

    return episRestTemplate.postForObject(
        url, new HttpEntity<>(mandate, getUserHeaders()), ApplicationResponseDTO.class);
  }

  @CacheEvict(value = CONTACT_DETAILS_CACHE_NAME, key = "#person.personalCode")
  public ContactDetails updateContactDetails(Person person, ContactDetails contactDetails) {
    String url = episServiceUrl + "/contact-details";

    log.info("Updating contact details for {}", contactDetails.getPersonalCode());

    return episRestTemplate.postForObject(
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

  private <T> List<T> toList(@Nullable T[] array) {
    return array != null ? Arrays.asList(array) : List.of();
  }
}
