package ee.tuleva.onboarding.kyb;

import static ee.tuleva.onboarding.kyb.KybCheckType.*;
import static ee.tuleva.onboarding.kyb.KybTestFixtures.JAAN;
import static ee.tuleva.onboarding.kyb.KybTestFixtures.boardMemberOwner;
import static ee.tuleva.onboarding.kyb.KybTestFixtures.companyWith;
import static ee.tuleva.onboarding.kyb.KybTestFixtures.shareholderOwner;
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
import ee.tuleva.onboarding.kyb.screener.DualMemberOwnershipScreener;
import ee.tuleva.onboarding.kyb.screener.RelatedPersonsKycScreener;
import ee.tuleva.onboarding.kyb.screener.SelfCertificationScreener;
import ee.tuleva.onboarding.kyb.screener.SoleBoardMemberIsOwnerScreener;
import ee.tuleva.onboarding.kyb.screener.SoleMemberOwnershipScreener;
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
    var person = boardMemberOwner("38501010001", 100.0).build();
    var data = companyWith(person);

    var results = kybScreeningService.screen(data);

    var types = results.stream().map(KybCheck::type).toList();
    assertThat(types)
        .containsExactlyInAnyOrder(
            COMPANY_STRUCTURE,
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
    var person1 = boardMemberOwner("38501010001", 50.0).build();
    var person2 = boardMemberOwner("38501010002", 50.0).build();
    var data = companyWith(person1, person2);

    var results = kybScreeningService.screen(data);

    var types = results.stream().map(KybCheck::type).toList();
    assertThat(types)
        .containsExactlyInAnyOrder(
            COMPANY_STRUCTURE,
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
    var person1 = boardMemberOwner("38501010001", 50.0).build();
    var person2 = shareholderOwner("38501010002", 50.0).build();
    var data = companyWith(person1, person2);

    var results = kybScreeningService.screen(data);

    var types = results.stream().map(KybCheck::type).toList();
    assertThat(types)
        .containsExactlyInAnyOrder(
            COMPANY_STRUCTURE,
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

  @Test
  void threePersonCompanyHasAtLeastOneFailingCheck() {
    var person1 = boardMemberOwner("38501010001", 40.0).build();
    var person2 = boardMemberOwner("38501010002", 30.0).build();
    var person3 = boardMemberOwner("38501010003", 30.0).build();
    var data = companyWith(person1, person2, person3);

    var results = kybScreeningService.screen(data);

    assertThat(results).anyMatch(check -> !check.success());
  }

  @Test
  void publishesKybCheckPerformedEvent() {
    var person = boardMemberOwner(JAAN, 100.0).build();
    var data = companyWith(person);

    var results = kybScreeningService.screen(data);

    var captor = ArgumentCaptor.forClass(KybCheckPerformedEvent.class);
    verify(eventPublisher).publishEvent(captor.capture());
    var event = captor.getValue();
    assertThat(event.getCompany()).isEqualTo(data.company());
    assertThat(event.getPersonalCode()).isEqualTo(JAAN);
    assertThat(event.getRelatedPersons()).isEqualTo(data.relatedPersons());
    assertThat(event.getChecks()).isEqualTo(results);
  }

  @Test
  void validateReturnsScreenerResultsWithoutPublishingEvent() {
    var person = boardMemberOwner("38501010001", 100.0).build();
    var data = companyWith(person);

    var results = kybScreeningService.validate(data);

    var types = results.stream().map(KybCheck::type).toList();
    assertThat(types)
        .containsExactlyInAnyOrder(
            COMPANY_STRUCTURE,
            SOLE_MEMBER_OWNERSHIP,
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
}
