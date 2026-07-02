package ee.tuleva.onboarding.kyb;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

import ee.tuleva.onboarding.company.Company;
import ee.tuleva.onboarding.company.CompanyRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class KybMonitoringServiceTest {

  private static final Instant NOW = Instant.parse("2026-07-02T00:00:00Z");

  private final LegalEntityScreener legalEntityScreener = mock(LegalEntityScreener.class);
  private final CompanyRepository companyRepository = mock(CompanyRepository.class);
  private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);

  private final KybMonitoringService service =
      new KybMonitoringService(
          legalEntityScreener, companyRepository, eventPublisher, Clock.fixed(NOW, ZoneOffset.UTC));

  @Test
  void screenAllCompaniesScreensEachCompany() {
    var company1 = Company.builder().registryCode("11111111").name("Company 1").build();
    var company2 = Company.builder().registryCode("22222222").name("Company 2").build();
    given(companyRepository.findAll()).willReturn(List.of(company1, company2));

    service.screenAllCompanies();

    verify(legalEntityScreener).screenLatest("11111111");
    verify(legalEntityScreener).screenLatest("22222222");
  }

  @Test
  void screenAllCompaniesContinuesWhenOneCompanyFails() {
    var company1 = Company.builder().registryCode("11111111").name("Company 1").build();
    var company2 = Company.builder().registryCode("22222222").name("Company 2").build();
    given(companyRepository.findAll()).willReturn(List.of(company1, company2));
    doThrow(new IllegalStateException("boom")).when(legalEntityScreener).screenLatest("11111111");

    service.screenAllCompanies();

    verify(legalEntityScreener).screenLatest("11111111");
    verify(legalEntityScreener).screenLatest("22222222");
  }

  @Test
  void screenAllCompaniesHandlesEmptyCompanyList() {
    given(companyRepository.findAll()).willReturn(List.of());

    service.screenAllCompanies();

    verifyNoInteractions(legalEntityScreener);
  }

  @Test
  void screenAllCompaniesPublishesCompletionEventWithRunStartTime() {
    var company = Company.builder().registryCode("11111111").name("Company 1").build();
    given(companyRepository.findAll()).willReturn(List.of(company));

    service.screenAllCompanies();

    verify(eventPublisher).publishEvent(new KybMonitoringCompletedEvent(NOW));
  }

  @Test
  void screenAllCompaniesPublishesCompletionEventEvenWhenScreeningsFail() {
    var company = Company.builder().registryCode("11111111").name("Company 1").build();
    given(companyRepository.findAll()).willReturn(List.of(company));
    doThrow(new IllegalStateException("boom")).when(legalEntityScreener).screenLatest("11111111");

    service.screenAllCompanies();

    verify(eventPublisher).publishEvent(new KybMonitoringCompletedEvent(NOW));
  }
}
