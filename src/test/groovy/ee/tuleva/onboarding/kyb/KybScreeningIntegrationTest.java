package ee.tuleva.onboarding.kyb;

import static ee.tuleva.onboarding.aml.AmlCheckType.*;
import static ee.tuleva.onboarding.kyb.CompanyStatus.R;
import static ee.tuleva.onboarding.kyb.KybKycStatus.*;
import static ee.tuleva.onboarding.time.ClockHolder.aYearAgo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import ee.tuleva.onboarding.aml.AmlCheck;
import ee.tuleva.onboarding.aml.AmlCheckRepository;
import ee.tuleva.onboarding.aml.sanctions.MatchResponse;
import ee.tuleva.onboarding.aml.sanctions.PepAndSanctionCheckService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@Transactional
class KybScreeningIntegrationTest {

  private static final PersonalCode PERSONAL_CODE = new PersonalCode("38501010002");

  @Autowired private KybScreeningService kybScreeningService;
  @Autowired private AmlCheckRepository amlCheckRepository;
  @Autowired private JsonMapper objectMapper;
  @MockitoBean private PepAndSanctionCheckService sanctionCheckService;

  @BeforeEach
  void setUp() {
    when(sanctionCheckService.matchCompany(any()))
        .thenReturn(
            new MatchResponse(objectMapper.createArrayNode(), objectMapper.createObjectNode()));
  }

  @Test
  void singlePersonCompanyWithValidOwnershipAndCompletedKycCreatesSuccessfulChecks() {
    var person =
        new KybRelatedPerson(PERSONAL_CODE, true, true, true, BigDecimal.valueOf(100), COMPLETED);
    var data =
        new KybCompanyData(
            new CompanyDto(new RegistryCode("12345678"), "Test OÜ", "62011", LegalForm.OÜ),
            PERSONAL_CODE,
            R,
            List.of(person),
            new SelfCertification(true, true, true));

    var results = kybScreeningService.screen(data);

    assertThat(results).hasSize(9).allMatch(KybCheck::success);

    var amlChecks =
        amlCheckRepository.findAllByPersonalCodeAndCreatedTimeAfter(
            PERSONAL_CODE.value(), aYearAgo());
    var types = amlChecks.stream().map(AmlCheck::getType).toList();
    assertThat(types)
        .containsExactlyInAnyOrder(
            KYB_COMPANY_ACTIVE,
            KYB_SOLE_MEMBER_OWNERSHIP,
            KYB_RELATED_PERSONS_KYC,
            KYB_COMPANY_SANCTION,
            KYB_COMPANY_PEP,
            KYB_HIGH_RISK_NACE,
            KYB_COMPANY_LEGAL_FORM,
            KYB_SELF_CERTIFICATION,
            KYB_DATA_CHANGED);
    assertThat(amlChecks).allMatch(AmlCheck::isSuccess);
  }

  @Test
  void singlePersonCompanyWithInvalidOwnershipCreatesFailedCheck() {
    var person =
        new KybRelatedPerson(PERSONAL_CODE, true, true, false, BigDecimal.valueOf(100), COMPLETED);
    var data =
        new KybCompanyData(
            new CompanyDto(new RegistryCode("12345678"), "Test OÜ", "62011", LegalForm.OÜ),
            PERSONAL_CODE,
            R,
            List.of(person),
            new SelfCertification(true, true, true));

    var results = kybScreeningService.screen(data);

    assertThat(results).filteredOn(c -> !c.success()).hasSize(1);

    var amlChecks =
        amlCheckRepository.findAllByPersonalCodeAndCreatedTimeAfter(
            PERSONAL_CODE.value(), aYearAgo());
    var failedCheck =
        amlChecks.stream().filter(c -> c.getType() == KYB_SOLE_MEMBER_OWNERSHIP).findFirst();
    assertThat(failedCheck).isPresent();
    assertThat(failedCheck.get().isSuccess()).isFalse();
  }

  @Test
  @SuppressWarnings("unchecked")
  void dataChangedCheckDetectsChangesOnRerun() {
    var person =
        new KybRelatedPerson(PERSONAL_CODE, true, true, true, BigDecimal.valueOf(100), COMPLETED);
    var data =
        new KybCompanyData(
            new CompanyDto(new RegistryCode("12345678"), "Test OÜ", "62011", LegalForm.OÜ),
            PERSONAL_CODE,
            R,
            List.of(person),
            new SelfCertification(true, true, true));

    kybScreeningService.screen(data);

    var changedData =
        new KybCompanyData(
            new CompanyDto(new RegistryCode("12345678"), "Test OÜ", "62011", LegalForm.OÜ),
            PERSONAL_CODE,
            CompanyStatus.L,
            List.of(person),
            new SelfCertification(true, true, true));

    var secondResults = kybScreeningService.screen(changedData);

    var dataChangedCheck =
        secondResults.stream().filter(c -> c.type() == KybCheckType.DATA_CHANGED).findFirst();
    assertThat(dataChangedCheck).isPresent();
    assertThat(dataChangedCheck.get().success()).isFalse();

    var changes = (List<Map<String, Object>>) dataChangedCheck.get().metadata().get("changes");
    assertThat(changes).isNotEmpty();
    assertThat(changes).anyMatch(c -> "COMPANY_ACTIVE".equals(c.get("check")));
  }

  @Test
  void relatedPersonWithRejectedKycCreatesFailedKycCheck() {
    var person =
        new KybRelatedPerson(PERSONAL_CODE, true, true, true, BigDecimal.valueOf(100), REJECTED);
    var data =
        new KybCompanyData(
            new CompanyDto(new RegistryCode("12345678"), "Test OÜ", "62011", LegalForm.OÜ),
            PERSONAL_CODE,
            R,
            List.of(person),
            new SelfCertification(true, true, true));

    var results = kybScreeningService.screen(data);

    var kycCheck =
        results.stream().filter(c -> c.type() == KybCheckType.RELATED_PERSONS_KYC).findFirst();
    assertThat(kycCheck).isPresent();
    assertThat(kycCheck.get().success()).isFalse();

    var amlChecks =
        amlCheckRepository.findAllByPersonalCodeAndCreatedTimeAfter(
            PERSONAL_CODE.value(), aYearAgo());
    var kycAmlCheck =
        amlChecks.stream().filter(c -> c.getType() == KYB_RELATED_PERSONS_KYC).findFirst();
    assertThat(kycAmlCheck).isPresent();
    assertThat(kycAmlCheck.get().isSuccess()).isFalse();
  }
}
