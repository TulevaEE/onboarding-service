package ee.tuleva.onboarding.kyb;

import static ee.tuleva.onboarding.kyb.CompanyStatus.R;
import static ee.tuleva.onboarding.kyb.KybCheckType.*;
import static ee.tuleva.onboarding.kyb.KybKycStatus.COMPLETED;
import static ee.tuleva.onboarding.kyb.KybKycStatus.UNKNOWN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ee.tuleva.onboarding.aml.sanctions.MatchResponse;
import ee.tuleva.onboarding.aml.sanctions.PepAndSanctionCheckService;
import ee.tuleva.onboarding.kyb.screener.CompanyActiveScreener;
import ee.tuleva.onboarding.kyb.screener.CompanyLegalFormScreener;
import ee.tuleva.onboarding.kyb.screener.CompanyNaceScreener;
import ee.tuleva.onboarding.kyb.screener.CompanySanctionScreener;
import ee.tuleva.onboarding.kyb.screener.DualMemberOwnershipScreener;
import ee.tuleva.onboarding.kyb.screener.RelatedPersonsKycScreener;
import ee.tuleva.onboarding.kyb.screener.SelfCertificationScreener;
import ee.tuleva.onboarding.kyb.screener.SoleBoardMemberIsOwnerScreener;
import ee.tuleva.onboarding.kyb.screener.SoleMemberOwnershipScreener;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Disabled;
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
              new CompanyActiveScreener(),
              new SoleMemberOwnershipScreener(),
              new DualMemberOwnershipScreener(),
              new SoleBoardMemberIsOwnerScreener(),
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
  void singlePersonCompanyRunsRules31And34() {
    var person =
        new KybRelatedPerson(
            new PersonalCode("38501010001"), true, true, true, BigDecimal.valueOf(100), UNKNOWN);
    var data =
        new KybCompanyData(
            new CompanyDto(new RegistryCode("12345678"), "Test OÜ", "62011", LegalForm.OÜ),
            new PersonalCode("38501010001"),
            R,
            List.of(person),
            new SelfCertification(true, true, true));

    var results = kybScreeningService.screen(data);

    var types = results.stream().map(KybCheck::type).toList();
    assertThat(types)
        .containsExactlyInAnyOrder(
            SOLE_MEMBER_OWNERSHIP,
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
  void twoPersonCompanyWithTwoBoardMembersRunsRules32And34() {
    var person1 =
        new KybRelatedPerson(
            new PersonalCode("38501010001"), true, true, true, BigDecimal.valueOf(50), UNKNOWN);
    var person2 =
        new KybRelatedPerson(
            new PersonalCode("38501010002"), true, true, true, BigDecimal.valueOf(50), UNKNOWN);
    var data =
        new KybCompanyData(
            new CompanyDto(new RegistryCode("12345678"), "Test OÜ", "62011", LegalForm.OÜ),
            new PersonalCode("38501010001"),
            R,
            List.of(person1, person2),
            new SelfCertification(true, true, true));

    var results = kybScreeningService.screen(data);

    var types = results.stream().map(KybCheck::type).toList();
    assertThat(types)
        .containsExactlyInAnyOrder(
            DUAL_MEMBER_OWNERSHIP,
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
  void twoPersonCompanyWithOneBoardMemberRunsRules33And34() {
    var person1 =
        new KybRelatedPerson(
            new PersonalCode("38501010001"), true, true, true, BigDecimal.valueOf(50), UNKNOWN);
    var person2 =
        new KybRelatedPerson(
            new PersonalCode("38501010002"), false, true, true, BigDecimal.valueOf(50), UNKNOWN);
    var data =
        new KybCompanyData(
            new CompanyDto(new RegistryCode("12345678"), "Test OÜ", "62011", LegalForm.OÜ),
            new PersonalCode("38501010001"),
            R,
            List.of(person1, person2),
            new SelfCertification(true, true, true));

    var results = kybScreeningService.screen(data);

    var types = results.stream().map(KybCheck::type).toList();
    assertThat(types)
        .containsExactlyInAnyOrder(
            SOLE_BOARD_MEMBER_IS_OWNER,
            COMPANY_ACTIVE,
            RELATED_PERSONS_KYC,
            COMPANY_SANCTION,
            COMPANY_PEP,
            HIGH_RISK_NACE,
            COMPANY_LEGAL_FORM,
            SELF_CERTIFICATION,
            DATA_CHANGED);
  }

  @Disabled("Companies with more than 2 people should not be supported")
  @Test
  void threePersonCompanyHasAtLeastOneFailingCheck() {
    var person1 =
        new KybRelatedPerson(
            new PersonalCode("38501010001"), true, true, true, BigDecimal.valueOf(40), COMPLETED);
    var person2 =
        new KybRelatedPerson(
            new PersonalCode("38501010002"), true, true, true, BigDecimal.valueOf(30), COMPLETED);
    var person3 =
        new KybRelatedPerson(
            new PersonalCode("38501010003"), true, true, true, BigDecimal.valueOf(30), COMPLETED);
    var data =
        new KybCompanyData(
            new CompanyDto(new RegistryCode("12345678"), "Test OÜ", "62011", LegalForm.OÜ),
            new PersonalCode("38501010001"),
            R,
            List.of(person1, person2, person3),
            new SelfCertification(true, true, true));

    var results = kybScreeningService.screen(data);

    assertThat(results).anyMatch(check -> !check.success());
  }

  @Test
  void publishesKybCheckPerformedEvent() {
    var person =
        new KybRelatedPerson(
            new PersonalCode("38501010001"), true, true, true, BigDecimal.valueOf(100), UNKNOWN);
    var data =
        new KybCompanyData(
            new CompanyDto(new RegistryCode("12345678"), "Test OÜ", "62011", LegalForm.OÜ),
            new PersonalCode("38501010001"),
            R,
            List.of(person),
            new SelfCertification(true, true, true));

    var results = kybScreeningService.screen(data);

    var captor = ArgumentCaptor.forClass(KybCheckPerformedEvent.class);
    verify(eventPublisher).publishEvent(captor.capture());
    var event = captor.getValue();
    assertThat(event.getPersonalCode()).isEqualTo(new PersonalCode("38501010001"));
    assertThat(event.getChecks()).isEqualTo(results);
  }
}
