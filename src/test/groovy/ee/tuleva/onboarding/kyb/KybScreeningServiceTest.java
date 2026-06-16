package ee.tuleva.onboarding.kyb;

import static ee.tuleva.onboarding.kyb.CompanyStatus.R;
import static ee.tuleva.onboarding.kyb.KybCheckType.*;
import static ee.tuleva.onboarding.kyb.KybKycStatus.COMPLETED;
import static ee.tuleva.onboarding.kyb.KybKycStatus.UNKNOWN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ee.tuleva.onboarding.aml.sanctions.MatchResponse;
import ee.tuleva.onboarding.aml.sanctions.PepAndSanctionCheckService;
import ee.tuleva.onboarding.kyb.screener.CompanyActiveScreener;
import ee.tuleva.onboarding.kyb.screener.CompanyLegalFormScreener;
import ee.tuleva.onboarding.kyb.screener.CompanyNaceScreener;
import ee.tuleva.onboarding.kyb.screener.CompanySanctionScreener;
import ee.tuleva.onboarding.kyb.screener.CompanyStructureScreener;
import ee.tuleva.onboarding.kyb.screener.RelatedPersonsKycScreener;
import ee.tuleva.onboarding.kyb.screener.SelfCertificationScreener;
import ee.tuleva.onboarding.kyb.screener.ShareholderEligibilityScreener;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import tools.jackson.databind.json.JsonMapper;

class KybScreeningServiceTest {

  private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
  private final PepAndSanctionCheckService sanctionCheckService =
      mock(PepAndSanctionCheckService.class);
  private final KybCheckHistory checkHistory = mock(KybCheckHistory.class);
  private final JsonMapper objectMapper = JsonMapper.builder().build();

  private final KybScreeningService kybScreeningService =
      new KybScreeningService(
          List.of(
              new CompanyStructureScreener(),
              new CompanyActiveScreener(),
              new ShareholderEligibilityScreener(),
              new RelatedPersonsKycScreener(),
              new CompanySanctionScreener(sanctionCheckService),
              new CompanyNaceScreener(),
              new CompanyLegalFormScreener(),
              new SelfCertificationScreener()),
          new KybDataChangeDetector(checkHistory),
          eventPublisher);

  {
    when(sanctionCheckService.matchCompany(any()))
        .thenReturn(
            new MatchResponse(objectMapper.createArrayNode(), objectMapper.createObjectNode()));
    when(checkHistory.getLatestChecks(any())).thenReturn(List.of());
  }

  @Test
  void singleOwnerDirectorRunsShareholderEligibility() {
    var data = companyWith(List.of(ownerDirector("38501010001", BigDecimal.valueOf(100))));

    var results = kybScreeningService.screen(data);

    assertThat(results.stream().map(KybCheck::type).toList())
        .containsExactlyInAnyOrder(
            COMPANY_STRUCTURE,
            SHAREHOLDER_ELIGIBILITY,
            COMPANY_ACTIVE,
            RELATED_PERSONS_KYC,
            COMPANY_SANCTION,
            COMPANY_PEP,
            HIGH_RISK_NACE,
            COMPANY_LEGAL_FORM,
            SELF_CERTIFICATION,
            DATA_CHANGED);
  }

  @Test
  void proAssetsShapeOwnerPlusNonOwnerDirectorPassesEveryCheck() {
    var owner = ownerDirector("38501010001", BigDecimal.valueOf(100), COMPLETED);
    var nonOwnerDirector =
        new KybRelatedPerson(
            new PersonalCode("48709232755"), true, false, false, BigDecimal.ZERO, COMPLETED);

    var results = kybScreeningService.screen(companyWith(List.of(owner, nonOwnerDirector)));

    assertThat(results).allMatch(KybCheck::success);
    assertThat(results.stream().map(KybCheck::type).toList())
        .contains(SHAREHOLDER_ELIGIBILITY, COMPANY_STRUCTURE);
  }

  @Test
  void threeRelatedPersonsExceedTheCapAndAreRejected() {
    var owner = ownerDirector("38501010001", BigDecimal.valueOf(100), COMPLETED);
    var secondDirector =
        new KybRelatedPerson(
            new PersonalCode("38501010002"), true, false, false, BigDecimal.ZERO, COMPLETED);
    var thirdDirector =
        new KybRelatedPerson(
            new PersonalCode("38501010003"), true, false, false, BigDecimal.ZERO, COMPLETED);

    var results =
        kybScreeningService.screen(companyWith(List.of(owner, secondDirector, thirdDirector)));

    assertThat(results).filteredOn(c -> c.type() == COMPANY_STRUCTURE).allMatch(c -> !c.success());
  }

  @Test
  void threeShareholdersHaveAtLeastOneFailingCheck() {
    var person1 = ownerDirector("38501010001", BigDecimal.valueOf(40), COMPLETED);
    var person2 = ownerDirector("38501010002", BigDecimal.valueOf(30), COMPLETED);
    var person3 = ownerDirector("38501010003", BigDecimal.valueOf(30), COMPLETED);

    var results = kybScreeningService.screen(companyWith(List.of(person1, person2, person3)));

    assertThat(results).anyMatch(check -> !check.success());
  }

  @Test
  void publishesKybCheckPerformedEvent() {
    var data = companyWith(List.of(ownerDirector("38501010001", BigDecimal.valueOf(100))));

    var results = kybScreeningService.screen(data);

    var captor = ArgumentCaptor.forClass(KybCheckPerformedEvent.class);
    verify(eventPublisher).publishEvent(captor.capture());
    var event = captor.getValue();
    assertThat(event.getCompany()).isEqualTo(data.company());
    assertThat(event.getPersonalCode()).isEqualTo(new PersonalCode("38501010001"));
    assertThat(event.getRelatedPersons()).isEqualTo(data.relatedPersons());
    assertThat(event.getChecks()).isEqualTo(results);
  }

  @Test
  void validateReturnsScreenerResultsWithoutPublishingEvent() {
    var data = companyWith(List.of(ownerDirector("38501010001", BigDecimal.valueOf(100))));

    var results = kybScreeningService.validate(data);

    var types = results.stream().map(KybCheck::type).toList();
    assertThat(types)
        .containsExactlyInAnyOrder(
            COMPANY_STRUCTURE,
            SHAREHOLDER_ELIGIBILITY,
            COMPANY_ACTIVE,
            RELATED_PERSONS_KYC,
            COMPANY_SANCTION,
            COMPANY_PEP,
            HIGH_RISK_NACE,
            COMPANY_LEGAL_FORM,
            SELF_CERTIFICATION);
    assertThat(types).doesNotContain(DATA_CHANGED);
    verifyNoInteractions(eventPublisher);
  }

  private KybRelatedPerson ownerDirector(String code, BigDecimal ownershipPercent) {
    return ownerDirector(code, ownershipPercent, UNKNOWN);
  }

  private KybRelatedPerson ownerDirector(
      String code, BigDecimal ownershipPercent, KybKycStatus kycStatus) {
    return new KybRelatedPerson(
        new PersonalCode(code), true, true, true, ownershipPercent, kycStatus);
  }

  private KybCompanyData companyWith(List<KybRelatedPerson> persons) {
    return new KybCompanyData(
        new CompanyDto(new RegistryCode("12345678"), "Test OÜ", "62011", LegalForm.OÜ),
        new PersonalCode("38501010001"),
        R,
        persons,
        new SelfCertification(true, true, true),
        "EE",
        "Harju maakond, Tallinn, Pärnu mnt 1",
        null);
  }
}
