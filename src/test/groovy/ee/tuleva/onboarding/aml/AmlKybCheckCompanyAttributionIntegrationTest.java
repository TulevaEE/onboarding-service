package ee.tuleva.onboarding.aml;

import static ee.tuleva.onboarding.kyb.KybCheckType.COMPANY_ACTIVE;
import static ee.tuleva.onboarding.kyb.KybCheckType.COMPANY_STRUCTURE;
import static ee.tuleva.onboarding.kyb.KybTestFixtures.boardMemberOwner;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import ee.tuleva.onboarding.aml.sanctions.PepAndSanctionCheckService;
import ee.tuleva.onboarding.company.Company;
import ee.tuleva.onboarding.company.CompanyRepository;
import ee.tuleva.onboarding.conversion.UserConversionService;
import ee.tuleva.onboarding.kyb.CompanyDto;
import ee.tuleva.onboarding.kyb.KybCheck;
import ee.tuleva.onboarding.kyb.KybCheckPerformedEvent;
import ee.tuleva.onboarding.kyb.LegalForm;
import ee.tuleva.onboarding.kyb.PersonalCode;
import ee.tuleva.onboarding.kyb.RegistryCode;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
public class AmlKybCheckCompanyAttributionIntegrationTest {

  @TestConfiguration
  static class TestConfig {
    @Bean
    @Primary
    public PepAndSanctionCheckService pepAndSanctionCheckService() {
      return mock(PepAndSanctionCheckService.class);
    }

    @Bean
    @Primary
    public UserConversionService userConversionService() {
      return mock(UserConversionService.class);
    }
  }

  @Autowired private ApplicationEventPublisher eventPublisher;
  @Autowired private AmlCheckRepository amlCheckRepository;
  @Autowired private CompanyRepository companyRepository;

  @Test
  @Transactional
  void firstScreeningAttributesNewlyCreatedCompanyIdToKybChecks() {
    var registryCode = "90000001";
    var personalCode = "38812121215";
    assertThat(companyRepository.findByRegistryCode(registryCode)).isEmpty();

    eventPublisher.publishEvent(
        screening(
            registryCode,
            personalCode,
            List.of(
                new KybCheck(COMPANY_ACTIVE, true, Map.of()),
                new KybCheck(COMPANY_STRUCTURE, true, Map.of()))));

    UUID companyId =
        companyRepository.findByRegistryCode(registryCode).map(Company::getId).orElseThrow();
    var checks =
        amlCheckRepository.findAllByPersonalCodeAndCreatedTimeAfter(
            personalCode, Instant.now().minusSeconds(3600));
    assertThat(checks).isNotEmpty();
    assertThat(checks).extracting(AmlCheck::getCompanyId).containsOnly(companyId);
  }

  @Test
  @Transactional
  void failingScreeningStillCreatesCompanyAndAttributesItsIdToKybChecks() {
    var registryCode = "90000002";
    var personalCode = "38812121215";
    assertThat(companyRepository.findByRegistryCode(registryCode)).isEmpty();

    eventPublisher.publishEvent(
        screening(
            registryCode,
            personalCode,
            List.of(
                new KybCheck(COMPANY_ACTIVE, false, Map.of()),
                new KybCheck(COMPANY_STRUCTURE, true, Map.of()))));

    UUID companyId =
        companyRepository.findByRegistryCode(registryCode).map(Company::getId).orElseThrow();
    var checks =
        amlCheckRepository.findAllByPersonalCodeAndCreatedTimeAfter(
            personalCode, Instant.now().minusSeconds(3600));
    assertThat(checks).isNotEmpty();
    assertThat(checks).extracting(AmlCheck::getCompanyId).containsOnly(companyId);
  }

  private KybCheckPerformedEvent screening(
      String registryCode, String personalCode, List<KybCheck> checks) {
    var company = new CompanyDto(new RegistryCode(registryCode), "Test OÜ", "62011", LegalForm.OÜ);
    var relatedPersons = List.of(boardMemberOwner(personalCode, 100.0).build());
    return new KybCheckPerformedEvent(
        this, company, new PersonalCode(personalCode), relatedPersons, checks, List.of());
  }
}
