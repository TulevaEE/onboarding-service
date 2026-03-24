package ee.tuleva.onboarding.kyb;

import static ee.tuleva.onboarding.kyb.CompanyStatus.R;
import static ee.tuleva.onboarding.kyb.KybCheckType.*;
import static ee.tuleva.onboarding.kyb.KybKycStatus.UNKNOWN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import ee.tuleva.onboarding.kyb.screener.CompanyActiveScreener;
import ee.tuleva.onboarding.kyb.screener.DualMemberOwnershipScreener;
import ee.tuleva.onboarding.kyb.screener.RelatedPersonsKycScreener;
import ee.tuleva.onboarding.kyb.screener.SoleBoardMemberIsOwnerScreener;
import ee.tuleva.onboarding.kyb.screener.SoleMemberOwnershipScreener;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

class KybScreeningServiceTest {

  private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);

  private final KybScreeningService kybScreeningService =
      new KybScreeningService(
          List.of(
              new CompanyActiveScreener(),
              new SoleMemberOwnershipScreener(),
              new DualMemberOwnershipScreener(),
              new SoleBoardMemberIsOwnerScreener(),
              new RelatedPersonsKycScreener()),
          eventPublisher);

  @Test
  void singlePersonCompanyRunsRules31And34() {
    var person =
        new KybRelatedPerson("38501010001", true, true, true, BigDecimal.valueOf(100), UNKNOWN);
    var data = new KybCompanyData("12345678", "38501010001", R, List.of(person));

    var results = kybScreeningService.screen(data);

    var types = results.stream().map(KybCheck::type).toList();
    assertThat(types)
        .containsExactlyInAnyOrder(SOLE_MEMBER_OWNERSHIP, COMPANY_ACTIVE, RELATED_PERSONS_KYC);
  }

  @Test
  void twoPersonCompanyWithTwoBoardMembersRunsRules32And34() {
    var person1 =
        new KybRelatedPerson("38501010001", true, true, true, BigDecimal.valueOf(50), UNKNOWN);
    var person2 =
        new KybRelatedPerson("38501010002", true, true, true, BigDecimal.valueOf(50), UNKNOWN);
    var data = new KybCompanyData("12345678", "38501010001", R, List.of(person1, person2));

    var results = kybScreeningService.screen(data);

    var types = results.stream().map(KybCheck::type).toList();
    assertThat(types)
        .containsExactlyInAnyOrder(DUAL_MEMBER_OWNERSHIP, COMPANY_ACTIVE, RELATED_PERSONS_KYC);
  }

  @Test
  void twoPersonCompanyWithOneBoardMemberRunsRules33And34() {
    var person1 =
        new KybRelatedPerson("38501010001", true, true, true, BigDecimal.valueOf(50), UNKNOWN);
    var person2 =
        new KybRelatedPerson("38501010002", false, true, true, BigDecimal.valueOf(50), UNKNOWN);
    var data = new KybCompanyData("12345678", "38501010001", R, List.of(person1, person2));

    var results = kybScreeningService.screen(data);

    var types = results.stream().map(KybCheck::type).toList();
    assertThat(types)
        .containsExactlyInAnyOrder(SOLE_BOARD_MEMBER_IS_OWNER, COMPANY_ACTIVE, RELATED_PERSONS_KYC);
  }

  @Test
  void publishesKybCheckPerformedEvent() {
    var person =
        new KybRelatedPerson("38501010001", true, true, true, BigDecimal.valueOf(100), UNKNOWN);
    var data = new KybCompanyData("12345678", "38501010001", R, List.of(person));

    var results = kybScreeningService.screen(data);

    var captor = ArgumentCaptor.forClass(KybCheckPerformedEvent.class);
    verify(eventPublisher).publishEvent(captor.capture());
    var event = captor.getValue();
    assertThat(event.getPersonalCode()).isEqualTo("38501010001");
    assertThat(event.getChecks()).isEqualTo(results);
  }
}
