package ee.tuleva.onboarding.epis;

import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.currency.Currency;
import ee.tuleva.onboarding.epis.account.FundBalanceDto;
import ee.tuleva.onboarding.epis.application.ApplicationResponse;
import ee.tuleva.onboarding.epis.cancellation.CancellationDto;
import ee.tuleva.onboarding.epis.cashflows.CashFlow;
import ee.tuleva.onboarding.epis.cashflows.CashFlowStatement;
import ee.tuleva.onboarding.epis.contact.ContactDetails;
import ee.tuleva.onboarding.epis.fund.FundDto;
import ee.tuleva.onboarding.epis.fund.NavDto;
import ee.tuleva.onboarding.epis.mandate.ApplicationDTO;
import ee.tuleva.onboarding.epis.mandate.ApplicationResponseDTO;
import ee.tuleva.onboarding.epis.mandate.ApplicationStatus;
import ee.tuleva.onboarding.epis.mandate.MandateDto;
import ee.tuleva.onboarding.fund.Fund;
import ee.tuleva.onboarding.fund.manager.FundManager;
import ee.tuleva.onboarding.mandate.application.ApplicationType;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.OAuth2RestOperations;
import org.springframework.security.oauth2.provider.authentication.OAuth2AuthenticationDetails;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestOperations;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static ee.tuleva.onboarding.epis.cashflows.CashFlow.Type.CASH;
import static ee.tuleva.onboarding.epis.cashflows.CashFlow.Type.CONTRIBUTION_CASH;
import static ee.tuleva.onboarding.epis.fund.FundDto.FundStatus.ACTIVE;
import static java.time.LocalDate.parse;

@Service
@Slf4j
@Profile("mock")
public class MockEpisService extends EpisService {

  private final String APPLICATIONS_CACHE_NAME = "applications";
  private final String TRANSFER_APPLICATIONS_CACHE_NAME = "transferApplications";
  private final String CONTACT_DETAILS_CACHE_NAME = "contactDetails";
  private final String ACCOUNT_STATEMENT_CACHE_NAME = "accountStatement";
  private final String CASH_FLOW_STATEMENT_CACHE_NAME = "cashFlowStatement";
  private final String FUNDS_CACHE_NAME = "funds";

  @Value("${epis.service.url}")
  String episServiceUrl;

  public MockEpisService(RestOperations userTokenRestTemplate, OAuth2RestOperations clientCredentialsRestTemplate) {
    super(userTokenRestTemplate, clientCredentialsRestTemplate);
  }

  @Cacheable(value = APPLICATIONS_CACHE_NAME, key = "#person.personalCode", sync = true)
  public List<ApplicationDTO> getApplications(Person person) {
    return List.of(
        ApplicationDTO.builder()
            .date(Instant.parse("2001-01-02T01:23:45Z"))
            .type(ApplicationType.SELECTION)
            .status(ApplicationStatus.COMPLETE)
            .id(123L)
            .currency("EUR")
            .sourceFundIsin("source")
        .build()
    );
  }

  @Cacheable(
      value = CASH_FLOW_STATEMENT_CACHE_NAME,
      key = "{ #person.personalCode, #fromDate, #toDate }",
      sync = true)
  public CashFlowStatement getCashFlowStatement(
      Person person, LocalDate fromDate, LocalDate toDate) {

    val time = Instant.parse("2022-01-02T01:23:45Z");
    val currency = Currency.EUR.name();
    val amount = new BigDecimal("2000");

    return CashFlowStatement.builder()
        .startBalance(Map.of(
        "1", CashFlow.builder().time(time).priceTime(time).amount(new BigDecimal("1000.0")).currency(currency).isin(null).build()
        ))
        .endBalance(Map.of(
        "1", CashFlow.builder().time(time).priceTime(time).amount(new BigDecimal("1100.0")).currency(currency).isin(null).build()
        ))
        .transactions(List.of(
        CashFlow.builder().time(time).priceTime(time).amount(new BigDecimal("2000.0")).currency(currency).isin(null).type(CASH).build(),
        CashFlow.builder().time(time.plusSeconds(1)).priceTime(time.plusSeconds(1)).amount(amount.negate()).currency(currency).isin(null).type(CASH).build(),
        CashFlow.builder().time(time.plusSeconds(1)).priceTime(time.plusSeconds(1)).amount(BigDecimal.valueOf(10.01)).currency(currency).isin("EE3600001707").type(CONTRIBUTION_CASH).build()
      )).build();

//    return CashFlowStatement.builder()
//        .startBalance(Map.of(
//        "1", CashFlow.builder().time(randomTime).priceTime(priceTime).amount(new BigDecimal("1000.0")).currency("EUR").isin("EE3600001707").build(),
//        "2", CashFlow.builder().time(randomTime).priceTime(priceTime).amount(new BigDecimal("115.0")).currency("EUR").isin("2").build(),
//        "3", CashFlow.builder().time(randomTime).priceTime(priceTime).amount(new BigDecimal("225.0")).currency("EUR").isin("3").build()
//        ))
//        .endBalance(Map.of(
//        "1", CashFlow.builder().time(randomTime).priceTime(priceTime).amount(new BigDecimal("1100.0")).currency("EUR").isin("EE3600001707").build(),
//        "2", CashFlow.builder().time(randomTime).priceTime(priceTime).amount(new BigDecimal("125.0")).currency("EUR").isin("2").build(),
//        "3", CashFlow.builder().time(randomTime).priceTime(priceTime).amount(new BigDecimal("250.0")).currency("EUR").isin("3").build()
//        ))
//        .transactions(List.of(
//        CashFlow.builder().time(randomTime).priceTime(priceTime).amount(new BigDecimal("-100.0")).currency("EUR").isin("EE3600001707").build(),
//        CashFlow.builder().time(randomTime).priceTime(priceTime).amount(new BigDecimal("-20.0")).currency("EUR").isin("2").build(),
//        CashFlow.builder().time(randomTime).priceTime(priceTime).amount(new BigDecimal("-25.0")).currency("EUR").isin("").build()
//            )).build();
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
    return mockContactDetails();
  }

  @Cacheable(value = CONTACT_DETAILS_CACHE_NAME, key = "#person.personalCode")
  public ContactDetails getContactDetails(Person person, String token) {
    return mockContactDetails();
  }

  private ContactDetails mockContactDetails() {
    return ContactDetails.builder()
        .firstName("Erko")
        .lastName("Risthein")
        .personalCode("38501010002")
        .addressRow1("Tuleva, Telliskivi 60")
        .country("EE")
        .postalIndex("10412")
        .districtCode("0784")
        .contactPreference(ContactDetails.ContactPreferenceType.valueOf("E"))
        .languagePreference(ContactDetails.LanguagePreferenceType.valueOf("EST"))
        .noticeNeeded("Y")
        .email("tuleva@tuleva.ee")
        .phoneNumber("+372546545")
        .pensionAccountNumber("993432432")
        .thirdPillarDistribution(List.of(new ContactDetails.Distribution("EE123", BigDecimal.ONE)))
        .isSecondPillarActive(true)
        .isThirdPillarActive(true)
        .build();
  }

  @Cacheable(value = ACCOUNT_STATEMENT_CACHE_NAME, key = "#person.personalCode")
  public List<FundBalanceDto> getAccountStatement(Person person) {
    return List.of(
        FundBalanceDto.builder()
            .isin("EE3600109435")
            .value(BigDecimal.valueOf(123.0))
            .unavailableValue(BigDecimal.valueOf(234.0))
            .units(BigDecimal.valueOf(345.0))
            .nav(BigDecimal.valueOf(0.12345))
            .currency("EUR")
            .activeContributions(true)
            .build(),
        FundBalanceDto.builder()
            .isin("EE3600001707")
            .value(BigDecimal.valueOf(123.0))
            .unavailableValue(BigDecimal.valueOf(234.0))
            .units(BigDecimal.valueOf(345.0))
            .nav(BigDecimal.valueOf(0.12345))
            .currency("EUR")
            .activeContributions(true)
            .build()
    );
  }

  @Cacheable(value = FUNDS_CACHE_NAME, unless = "#result.isEmpty()")
  public List<FundDto> getFunds() {
    return List.of(new FundDto("EE3600109435", "Tuleva Maailma Aktsiate Pensionifond", "TUK75", 2, ACTIVE));
//    return sampleFunds();
  }

  public NavDto getNav(String isin, LocalDate date) {
    return new NavDto(isin, parse("2019-08-19"), new BigDecimal("19.0"));
  }

  public ApplicationResponseDTO sendMandate(MandateDto mandate) {
    return new ApplicationResponseDTO();
  }

  public ApplicationResponse sendCancellation(CancellationDto cancellation) {
    return new ApplicationResponse();
//    String url = episServiceUrl + "/cancellations";
//
//    return userTokenRestTemplate.postForObject(
//        url, new HttpEntity<>(cancellation, getHeaders()), ApplicationResponse.class);
  }

  @CacheEvict(value = CONTACT_DETAILS_CACHE_NAME, key = "#person.personalCode")
  public ContactDetails updateContactDetails(Person person, ContactDetails contactDetails) {
    return mockContactDetails();
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

  List<Fund> sampleFunds() {
    return List.of(
        Fund.builder()
            .isin("AE123232334")
            .nameEstonian("Tuleva maailma aktsiate pensionifond")
            .nameEnglish("Tuleva World Stock Fund")
            .shortName("TUK75")
            .id(123L)
            .fundManager(
                FundManager.builder()
                    .id(123L)
                    .name("Tuleva")
                    .build()
            )
            .pillar(2)
            .build(),
        Fund.builder()
            .isin("EE3600109443")
            .nameEstonian("Tuleva maailma võlakirjade pensionifond")
            .nameEnglish("Tuleva World Bonds Pension Fund")
            .shortName("TUK00")
            .id(234L)
            .fundManager(
                FundManager.builder()
                    .id(123L)
                    .name("Tuleva")
                    .build()
            )
            .pillar(2)
            .build(),
        Fund.builder()
            .isin("EE3600019775")
            .nameEstonian("SEB fond")
            .nameEnglish("SEB fund")
            .shortName("SEB123")
            .fundManager(
                FundManager.builder()
                    .id(124L)
                    .name("SEB")
                    .build()
            )
            .pillar(2)
            .build(),
        Fund.builder()
            .isin("EE3600019776")
            .nameEstonian("LHV XL")
            .nameEnglish("LHV XL eng")
            .shortName("LXK75")
            .fundManager(
                FundManager.builder()
                    .id(125L)
                    .name("LHV")
                    .build()
            )
            .pillar(2)
            .build(),
        Fund.builder()
            .isin("EE3600019777")
            .nameEstonian("Swedbänk fond")
            .nameEnglish("Swedbank fund")
            .shortName("SWE123")
            .fundManager(
                FundManager.builder()
                    .id(126L)
                    .name("Swedbank")
                    .build()
            )
            .pillar(2)
            .build(),
        Fund.builder()
            .isin("AE123232331")
            .nameEstonian("Nordea fond")
            .nameEnglish("Nordea fund")
            .shortName("ND123")
            .fundManager(
                FundManager.builder()
                    .id(127L)
                    .name("Nordea")
                    .build()
            )
            .pillar(2)
            .build(),
        Fund.builder()
            .isin("AE123232337")
            .nameEstonian("LHV S")
            .nameEnglish("LHV S eng")
            .shortName("LXK00")
            .fundManager(
                FundManager.builder()
                    .id(125L)
                    .name("LHV")
                    .build()
            )
            .pillar(2)
            .build()
    );
  }

}
