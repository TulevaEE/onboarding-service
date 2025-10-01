package ee.tuleva.onboarding.epis;

import ee.tuleva.onboarding.auth.jwt.JwtTokenUtil;
import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.epis.account.FundBalanceDto;
import ee.tuleva.onboarding.epis.cashflows.CashFlowStatement;
import ee.tuleva.onboarding.epis.contact.ContactDetails;
import ee.tuleva.onboarding.epis.fund.FundDto;
import ee.tuleva.onboarding.epis.fund.NavDto;
import ee.tuleva.onboarding.epis.mandate.ApplicationDTO;
import ee.tuleva.onboarding.epis.mandate.ApplicationResponseDTO;
import ee.tuleva.onboarding.epis.mandate.MandateDto;
import ee.tuleva.onboarding.epis.transaction.ExchangeTransactionDto;
import ee.tuleva.onboarding.epis.transaction.FundTransactionDto;
import ee.tuleva.onboarding.epis.transaction.ThirdPillarTransactionDto;
import ee.tuleva.onboarding.epis.transaction.TransactionFundBalanceDto;
import ee.tuleva.onboarding.epis.transaction.UnitOwnerDto;
import ee.tuleva.onboarding.epis.withdrawals.ArrestsBankruptciesDto;
import ee.tuleva.onboarding.epis.withdrawals.FundPensionCalculationDto;
import ee.tuleva.onboarding.epis.withdrawals.FundPensionStatusDto;
import java.lang.annotation.*;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.web.client.RestTemplate;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@ExtendWith(EnableEpisServiceHolder.ResetDelegateExtension.class)
@Import(EnableEpisServiceHolder.Config.class)
public @interface EnableEpisServiceHolder {

  @TestConfiguration
  class Config {

    @Bean
    @Primary
    public EpisServiceHolder episServiceHolder(
        RestTemplate restTemplate,
        JwtTokenUtil jwtTokenUtil,
        Optional<EpisService> existingEpisService) {
      return new EpisServiceHolder(restTemplate, jwtTokenUtil, existingEpisService.orElse(null));
    }
  }

  class EpisServiceHolder extends EpisService {
    private final RestTemplate restTemplate;
    private final JwtTokenUtil jwtTokenUtil;
    private final EpisService originalDelegate;
    private EpisService delegate;

    public EpisServiceHolder(
        RestTemplate restTemplate, JwtTokenUtil jwtTokenUtil, EpisService originalDelegate) {
      super(restTemplate, jwtTokenUtil);
      this.restTemplate = restTemplate;
      this.jwtTokenUtil = jwtTokenUtil;
      this.originalDelegate = originalDelegate;
      this.delegate = originalDelegate;
    }

    public <T extends EpisService> T createDelegate(
        BiFunction<RestTemplate, JwtTokenUtil, T> factory) {
      T newDelegate = factory.apply(restTemplate, jwtTokenUtil);
      this.delegate = newDelegate;
      return newDelegate;
    }

    public void resetDelegate() {
      this.delegate = originalDelegate;
    }

    @Override
    public List<ApplicationDTO> getApplications(Person person) {
      return delegate.getApplications(person);
    }

    @Override
    public CashFlowStatement getCashFlowStatement(
        Person person, LocalDate fromDate, LocalDate toDate) {
      return delegate.getCashFlowStatement(person, fromDate, toDate);
    }

    @Override
    public void clearCache(Person person) {
      delegate.clearCache(person);
    }

    @Override
    public ContactDetails getContactDetails(Person person) {
      return delegate.getContactDetails(person);
    }

    @Override
    public ContactDetails getContactDetails(Person person, String token) {
      return delegate.getContactDetails(person, token);
    }

    @Override
    public List<FundBalanceDto> getAccountStatement(
        Person person, LocalDate fromDate, LocalDate toDate) {
      return delegate.getAccountStatement(person, fromDate, toDate);
    }

    @Override
    public FundPensionCalculationDto getFundPensionCalculation(Person person) {
      return delegate.getFundPensionCalculation(person);
    }

    @Override
    public FundPensionStatusDto getFundPensionStatus(Person person) {
      return delegate.getFundPensionStatus(person);
    }

    @Override
    public ArrestsBankruptciesDto getArrestsBankruptciesPresent(Person person) {
      return delegate.getArrestsBankruptciesPresent(person);
    }

    @Override
    public List<FundDto> getFunds() {
      return delegate.getFunds();
    }

    @Override
    public NavDto getNav(String isin, LocalDate date) {
      return delegate.getNav(isin, date);
    }

    @Override
    public ApplicationResponseDTO sendMandate(MandateDto mandate) {
      return delegate.sendMandate(mandate);
    }

    @Override
    public ContactDetails updateContactDetails(Person person, ContactDetails contactDetails) {
      return delegate.updateContactDetails(person, contactDetails);
    }

    @Override
    public List<TransactionFundBalanceDto> getFundBalances(LocalDate requestDate) {
      return delegate.getFundBalances(requestDate);
    }

    @Override
    public List<UnitOwnerDto> getUnitOwners() {
      return delegate.getUnitOwners();
    }

    @Override
    public List<FundTransactionDto> getFundTransactions(
        String isin, LocalDate fromDate, LocalDate toDate) {
      return delegate.getFundTransactions(isin, fromDate, toDate);
    }

    @Override
    public List<ThirdPillarTransactionDto> getTransactions(LocalDate startDate, LocalDate endDate) {
      return delegate.getTransactions(startDate, endDate);
    }

    @Override
    public List<ExchangeTransactionDto> getExchangeTransactions(
        LocalDate startDate,
        Optional<String> securityFrom,
        Optional<String> securityTo,
        boolean pikFlag) {
      return delegate.getExchangeTransactions(startDate, securityFrom, securityTo, pikFlag);
    }
  }

  class ResetDelegateExtension implements AfterEachCallback {
    @Override
    public void afterEach(ExtensionContext context) throws Exception {
      SpringExtension.getApplicationContext(context)
          .getBean(EpisServiceHolder.class)
          .resetDelegate();
    }
  }
}
