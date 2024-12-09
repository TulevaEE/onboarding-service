package ee.tuleva.onboarding.epis;

import static ee.tuleva.onboarding.epis.cashflows.CashFlow.Type.CASH;
import static ee.tuleva.onboarding.epis.cashflows.CashFlow.Type.CONTRIBUTION_CASH;
import static ee.tuleva.onboarding.epis.fund.FundDto.FundStatus.ACTIVE;
import static ee.tuleva.onboarding.epis.mandate.ApplicationStatus.COMPLETE;
import static ee.tuleva.onboarding.epis.mandate.ApplicationStatus.PENDING;
import static ee.tuleva.onboarding.mandate.application.ApplicationType.*;
import static java.time.LocalDate.parse;

import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.currency.Currency;
import ee.tuleva.onboarding.epis.account.FundBalanceDto;
import ee.tuleva.onboarding.epis.application.ApplicationResponse;
import ee.tuleva.onboarding.epis.cashflows.CashFlow;
import ee.tuleva.onboarding.epis.cashflows.CashFlowStatement;
import ee.tuleva.onboarding.epis.contact.ContactDetails;
import ee.tuleva.onboarding.epis.fund.FundDto;
import ee.tuleva.onboarding.epis.fund.NavDto;
import ee.tuleva.onboarding.epis.mandate.ApplicationDTO;
import ee.tuleva.onboarding.epis.mandate.ApplicationDTO.FundPensionDetails;
import ee.tuleva.onboarding.epis.mandate.ApplicationResponseDTO;
import ee.tuleva.onboarding.epis.mandate.MandateDto;
import ee.tuleva.onboarding.epis.withdrawals.ArrestsBankruptciesDto;
import ee.tuleva.onboarding.epis.withdrawals.FundPensionCalculationDto;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

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
  private final String FUND_PENSION_CALCULATION_CACHE_NAME = "fundPensionCalculation";
  private final String ARRESTS_BANKRUPTCIES_CACHE_NAME = "arrestsBankruptcies";

  @Value("${epis.service.url}")
  String episServiceUrl;

  public MockEpisService(RestTemplate restTemplate) {
    super(restTemplate, null);
  }

  @Cacheable(value = APPLICATIONS_CACHE_NAME, key = "#person.personalCode", sync = true)
  public List<ApplicationDTO> getApplications(Person person) {
    return List.of(
        ApplicationDTO.builder()
            .date(Instant.parse("2001-01-02T01:23:45Z"))
            .type(SELECTION)
            .status(COMPLETE)
            .id(123L)
            .currency("EUR")
            .sourceFundIsin("source")
            .build(),
        ApplicationDTO.builder()
            .date(Instant.parse("2024-12-01T01:23:45Z"))
            .type(PARTIAL_WITHDRAWAL)
            .status(PENDING)
            .bankAccount("EE_TEST_IBAN")
            .id(124L)
            .build(),
        ApplicationDTO.builder()
            .date(Instant.parse("2024-12-01T01:23:45Z"))
            .type(WITHDRAWAL_THIRD_PILLAR)
            .status(PENDING)
            .bankAccount("EE_TEST_IBAN")
            .id(125L)
            .build(),
        ApplicationDTO.builder()
            .date(Instant.parse("2024-12-02T01:23:45Z"))
            .type(FUND_PENSION_OPENING)
            .status(PENDING)
            .bankAccount("EE_TEST_IBAN")
            .fundPensionDetails(new FundPensionDetails(20, 12))
            .id(126L)
            .build(),
        ApplicationDTO.builder()
            .date(Instant.parse("2024-12-02T01:23:45Z"))
            .type(FUND_PENSION_OPENING_THIRD_PILLAR)
            .status(PENDING)
            .bankAccount("EE_TEST_IBAN")
            .fundPensionDetails(new FundPensionDetails(20, 12))
            .id(127L)
            .build());
  }

  @Cacheable(
      value = CASH_FLOW_STATEMENT_CACHE_NAME,
      key = "{ #person.personalCode, #fromDate, #toDate }",
      sync = true)
  public CashFlowStatement getCashFlowStatement(
      Person person, LocalDate fromDate, LocalDate toDate) {

    final var time = Instant.parse("2022-01-02T01:23:45Z");
    final var currency = Currency.EUR;
    final var amount = new BigDecimal("2000");

    return CashFlowStatement.builder()
        .startBalance(
            Map.of(
                "1",
                CashFlow.builder()
                    .time(time)
                    .priceTime(time)
                    .amount(new BigDecimal("1000.0"))
                    .currency(currency)
                    .isin(null)
                    .build()))
        .endBalance(
            Map.of(
                "1",
                CashFlow.builder()
                    .time(time)
                    .priceTime(time)
                    .amount(new BigDecimal("1100.0"))
                    .currency(currency)
                    .isin(null)
                    .build()))
        .transactions(
            List.of(
                CashFlow.builder()
                    .time(time)
                    .priceTime(time)
                    .amount(new BigDecimal("2000.0"))
                    .currency(currency)
                    .isin(null)
                    .type(CASH)
                    .build(),
                CashFlow.builder()
                    .time(time.plusSeconds(1))
                    .priceTime(time.plusSeconds(1))
                    .amount(amount.negate())
                    .currency(currency)
                    .isin(null)
                    .type(CASH)
                    .build(),
                CashFlow.builder()
                    .time(time.plusSeconds(1))
                    .priceTime(time.plusSeconds(1))
                    .amount(BigDecimal.valueOf(10.01))
                    .currency(currency)
                    .isin("EE3600001707")
                    .type(CONTRIBUTION_CASH)
                    .build()))
        .build();
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
        .country("EE")
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
            .isin("EE3600019774")
            .value(BigDecimal.valueOf(245.0))
            .unavailableValue(BigDecimal.valueOf(234.0))
            .units(BigDecimal.valueOf(345.0))
            .nav(BigDecimal.valueOf(0.12345))
            .currency("EUR")
            .activeContributions(false)
            .build(),
        FundBalanceDto.builder()
            .isin("EE3600109443")
            .value(BigDecimal.valueOf(345.0))
            .unavailableValue(BigDecimal.valueOf(234.0))
            .units(BigDecimal.valueOf(345.0))
            .nav(BigDecimal.valueOf(0.12345))
            .currency("EUR")
            .activeContributions(false)
            .build(),
        FundBalanceDto.builder()
            .isin("EE3600001707")
            .value(BigDecimal.valueOf(123.0))
            .unavailableValue(BigDecimal.valueOf(234.0))
            .units(BigDecimal.valueOf(345.0))
            .nav(BigDecimal.valueOf(0.12345))
            .currency("EUR")
            .activeContributions(true)
            .build());
  }

  @Cacheable(value = FUND_PENSION_CALCULATION_CACHE_NAME, key = "#person.personalCode", sync = true)
  public FundPensionCalculationDto getFundPensionCalculation(Person person) {
    return new FundPensionCalculationDto(20);
  }

  @Cacheable(value = ARRESTS_BANKRUPTCIES_CACHE_NAME, key = "#person.personalCode", sync = true)
  public ArrestsBankruptciesDto getArrestsBankruptciesPresent(Person person) {
    return new ArrestsBankruptciesDto(false, false);
  }

  @Cacheable(value = FUNDS_CACHE_NAME, unless = "#result.isEmpty()")
  public List<FundDto> getFunds() {
    return List.of(
        new FundDto("EE3600109435", "Tuleva Maailma Aktsiate Pensionifond", "TUK75", 2, ACTIVE));
  }

  public NavDto getNav(String isin, LocalDate date) {
    return new NavDto(isin, parse("2019-08-19"), new BigDecimal("19.0"));
  }

  public ApplicationResponseDTO sendMandate(MandateDto mandate) {
    return new ApplicationResponseDTO();
  }

  public ApplicationResponse sendCancellation(ApplicationResponse cancellation) {
    return new ApplicationResponse();
  }

  @CacheEvict(value = CONTACT_DETAILS_CACHE_NAME, key = "#person.personalCode")
  public ContactDetails updateContactDetails(Person person, ContactDetails contactDetails) {
    return mockContactDetails();
  }
}
