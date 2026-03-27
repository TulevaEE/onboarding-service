package ee.tuleva.onboarding.savings.fund;

import static ee.tuleva.onboarding.kyb.KybCheckType.*;
import static ee.tuleva.onboarding.savings.fund.SavingsFundOnboardingStatus.COMPLETED;
import static ee.tuleva.onboarding.savings.fund.SavingsFundOnboardingStatus.REJECTED;
import static org.mockito.Mockito.*;

import ee.tuleva.onboarding.kyb.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LegalEntityOnboardingEventListenerTest {

  private final SavingsFundOnboardingRepository repository =
      mock(SavingsFundOnboardingRepository.class);
  private final LegalEntityOnboardingEventListener listener =
      new LegalEntityOnboardingEventListener(repository);

  private final CompanyDto company =
      new CompanyDto(new RegistryCode("12345678"), "Test OÜ", "62011", LegalForm.OÜ);

  private final List<KybRelatedPerson> relatedPersons =
      List.of(
          new KybRelatedPerson(
              new PersonalCode("38501010001"),
              true,
              true,
              true,
              BigDecimal.valueOf(100),
              KybKycStatus.COMPLETED));

  @Test
  void setsStatusCompletedWhenAllChecksPass() {
    var checks =
        List.of(
            new KybCheck(COMPANY_ACTIVE, true, Map.of()),
            new KybCheck(COMPANY_STRUCTURE, true, Map.of()));
    var event =
        new KybCheckPerformedEvent(
            this, company, new PersonalCode("38501010001"), relatedPersons, checks);

    listener.onKybCheckPerformed(event);

    verify(repository).saveOnboardingStatus("12345678", COMPLETED);
  }

  @Test
  void setsStatusRejectedWhenAnyCheckFails() {
    var checks =
        List.of(
            new KybCheck(COMPANY_ACTIVE, true, Map.of()),
            new KybCheck(COMPANY_STRUCTURE, false, Map.of()));
    var event =
        new KybCheckPerformedEvent(
            this, company, new PersonalCode("38501010001"), relatedPersons, checks);

    listener.onKybCheckPerformed(event);

    verify(repository).saveOnboardingStatus("12345678", REJECTED);
  }

  @Test
  void setsStatusRejectedEvenIfPreviouslyCompleted() {
    var checks =
        List.of(
            new KybCheck(COMPANY_ACTIVE, true, Map.of()),
            new KybCheck(COMPANY_STRUCTURE, false, Map.of()));
    var event =
        new KybCheckPerformedEvent(
            this, company, new PersonalCode("38501010001"), relatedPersons, checks);

    listener.onKybCheckPerformed(event);

    verify(repository).saveOnboardingStatus("12345678", REJECTED);
  }

  @Test
  void setsStatusCompletedWhenOnlyDataChangedCheckFails() {
    var checks =
        List.of(
            new KybCheck(COMPANY_ACTIVE, true, Map.of()),
            new KybCheck(COMPANY_STRUCTURE, true, Map.of()),
            new KybCheck(DATA_CHANGED, false, Map.of()));
    var event =
        new KybCheckPerformedEvent(
            this, company, new PersonalCode("38501010001"), relatedPersons, checks);

    listener.onKybCheckPerformed(event);

    verify(repository).saveOnboardingStatus("12345678", COMPLETED);
  }
}
