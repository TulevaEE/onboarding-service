package ee.tuleva.onboarding.company;

import static ee.tuleva.onboarding.company.RelationshipType.*;
import static ee.tuleva.onboarding.kyb.KybCheckType.*;
import static ee.tuleva.onboarding.party.PartyId.Type.PERSON;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

import ee.tuleva.onboarding.kyb.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class CompanyOnboardingEventListenerTest {

  private final CompanyRepository companyRepository = mock(CompanyRepository.class);
  private final CompanyPartyRepository companyPartyRepository = mock(CompanyPartyRepository.class);
  private final CompanyOnboardingEventListener listener =
      new CompanyOnboardingEventListener(companyRepository, companyPartyRepository);

  private final CompanyDto company =
      new CompanyDto(new RegistryCode("12345678"), "Test OÜ", "62011", LegalForm.OÜ);

  // board member + shareholder + beneficial owner
  private final KybRelatedPerson person1 =
      new KybRelatedPerson(
          new PersonalCode("38501010001"),
          true,
          true,
          true,
          BigDecimal.valueOf(50),
          KybKycStatus.COMPLETED);

  // board member only
  private final KybRelatedPerson person2 =
      new KybRelatedPerson(
          new PersonalCode("38501010002"),
          true,
          false,
          false,
          BigDecimal.ZERO,
          KybKycStatus.COMPLETED);

  // shareholder + beneficial owner, not a board member
  private final KybRelatedPerson person3 =
      new KybRelatedPerson(
          new PersonalCode("38501010003"),
          false,
          true,
          true,
          BigDecimal.valueOf(50),
          KybKycStatus.COMPLETED);

  @Test
  void createsCompanyAndAllPartyRelationshipsWhenAllChecksPass() {
    var checks =
        List.of(
            new KybCheck(COMPANY_ACTIVE, true, Map.of()),
            new KybCheck(COMPANY_STRUCTURE, true, Map.of()));
    var event =
        new KybCheckPerformedEvent(
            this,
            company,
            new PersonalCode("38501010001"),
            List.of(person1, person2, person3),
            checks);
    given(companyRepository.findByRegistryCode("12345678")).willReturn(Optional.empty());
    given(companyRepository.save(any(Company.class)))
        .willAnswer(invocation -> invocation.getArgument(0));

    listener.onKybCheckPerformed(event);

    verify(companyRepository).save(any(Company.class));

    var captor = ArgumentCaptor.forClass(CompanyParty.class);
    verify(companyPartyRepository, times(6)).save(captor.capture());

    var parties = captor.getAllValues();
    // person1: board member + shareholder + beneficial owner = 3
    assertThat(parties)
        .filteredOn(p -> p.getPartyCode().equals("38501010001"))
        .extracting(CompanyParty::getRelationshipType)
        .containsExactlyInAnyOrder(BOARD_MEMBER, SHAREHOLDER, BENEFICIAL_OWNER);
    // person2: board member = 1
    assertThat(parties)
        .filteredOn(p -> p.getPartyCode().equals("38501010002"))
        .extracting(CompanyParty::getRelationshipType)
        .containsExactlyInAnyOrder(BOARD_MEMBER);
    // person3: shareholder + beneficial owner = 2
    assertThat(parties)
        .filteredOn(p -> p.getPartyCode().equals("38501010003"))
        .extracting(CompanyParty::getRelationshipType)
        .containsExactlyInAnyOrder(SHAREHOLDER, BENEFICIAL_OWNER);
    // all are PERSON type
    assertThat(parties).allMatch(p -> p.getPartyType() == PERSON);
  }

  @Test
  void usesExistingCompanyIfAlreadyExists() {
    var existing = Company.builder().registryCode("12345678").name("Test OÜ").build();
    var checks = List.of(new KybCheck(COMPANY_ACTIVE, true, Map.of()));
    var event =
        new KybCheckPerformedEvent(
            this, company, new PersonalCode("38501010001"), List.of(person2), checks);
    given(companyRepository.findByRegistryCode("12345678")).willReturn(Optional.of(existing));

    listener.onKybCheckPerformed(event);

    verify(companyRepository, never()).save(any());
    verify(companyPartyRepository).save(any(CompanyParty.class));
  }

  @Test
  void replacesPartiesOnReScreeningWhenAllChecksPass() {
    var existing =
        Company.builder()
            .id(java.util.UUID.randomUUID())
            .registryCode("12345678")
            .name("Test OÜ")
            .build();
    var checks =
        List.of(
            new KybCheck(COMPANY_ACTIVE, true, Map.of()),
            new KybCheck(DATA_CHANGED, true, Map.of()));
    var event =
        new KybCheckPerformedEvent(
            this, company, new PersonalCode("38501010001"), List.of(person2), checks);
    given(companyRepository.findByRegistryCode("12345678")).willReturn(Optional.of(existing));

    listener.onKybCheckPerformed(event);

    verify(companyRepository, never()).save(any());
    var inOrder = inOrder(companyPartyRepository);
    inOrder.verify(companyPartyRepository).deleteByCompanyId(existing.getId());
    inOrder.verify(companyPartyRepository).save(any(CompanyParty.class));
  }

  @Test
  void replacesPartiesForExistingCompanyWhenDataChangedCheckFails() {
    var existing =
        Company.builder()
            .id(java.util.UUID.randomUUID())
            .registryCode("12345678")
            .name("Test OÜ")
            .build();
    var checks =
        List.of(
            new KybCheck(COMPANY_ACTIVE, true, Map.of()),
            new KybCheck(DATA_CHANGED, false, Map.of()));
    var event =
        new KybCheckPerformedEvent(
            this, company, new PersonalCode("38501010001"), List.of(person1), checks);
    given(companyRepository.findByRegistryCode("12345678")).willReturn(Optional.of(existing));

    listener.onKybCheckPerformed(event);

    verify(companyRepository, never()).save(any());
    verify(companyPartyRepository).deleteByCompanyId(existing.getId());
    verify(companyPartyRepository, times(3)).save(any(CompanyParty.class));
  }

  @Test
  void createsCompanyWhenDataChangedFailsAndCompanyDoesNotExist() {
    var checks =
        List.of(
            new KybCheck(COMPANY_ACTIVE, true, Map.of()),
            new KybCheck(DATA_CHANGED, false, Map.of()));
    var event =
        new KybCheckPerformedEvent(
            this, company, new PersonalCode("38501010001"), List.of(person1), checks);
    given(companyRepository.findByRegistryCode("12345678")).willReturn(Optional.empty());
    given(companyRepository.save(any(Company.class)))
        .willAnswer(invocation -> invocation.getArgument(0));

    listener.onKybCheckPerformed(event);

    verify(companyRepository).save(any(Company.class));
    verify(companyPartyRepository, times(3)).save(any(CompanyParty.class));
  }

  @Test
  void doesNothingWhenNonDataChangedCheckFails() {
    var checks =
        List.of(
            new KybCheck(COMPANY_ACTIVE, true, Map.of()),
            new KybCheck(COMPANY_STRUCTURE, false, Map.of()),
            new KybCheck(DATA_CHANGED, false, Map.of()));
    var event =
        new KybCheckPerformedEvent(
            this, company, new PersonalCode("38501010001"), List.of(person1), checks);

    listener.onKybCheckPerformed(event);

    verifyNoInteractions(companyRepository);
    verifyNoInteractions(companyPartyRepository);
  }

  @Test
  void doesNothingWhenNonDataChangedCheckFailsAndDataUnchanged() {
    var checks =
        List.of(
            new KybCheck(COMPANY_ACTIVE, true, Map.of()),
            new KybCheck(COMPANY_STRUCTURE, false, Map.of()));
    var event =
        new KybCheckPerformedEvent(
            this, company, new PersonalCode("38501010001"), List.of(person1), checks);

    listener.onKybCheckPerformed(event);

    verifyNoInteractions(companyRepository);
    verifyNoInteractions(companyPartyRepository);
  }
}
