package ee.tuleva.onboarding.payment.email;

import static ee.tuleva.onboarding.party.ParentChildLinkStatus.ACTIVE;
import static ee.tuleva.onboarding.party.ParentChildLinkStatus.PENDING_KYC;
import static ee.tuleva.onboarding.party.PartyId.Type.LEGAL_ENTITY;
import static ee.tuleva.onboarding.party.PartyId.Type.PERSON;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import ee.tuleva.onboarding.auth.role.CompanyRoles;
import ee.tuleva.onboarding.auth.role.CompanyRoles.CompanyRole;
import ee.tuleva.onboarding.party.ParentChildLinkService;
import ee.tuleva.onboarding.party.Party;
import ee.tuleva.onboarding.party.PartyId;
import ee.tuleva.onboarding.party.PartyResolver;
import ee.tuleva.onboarding.user.User;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SavingsFundAccountHolderResolverTest {

  @Mock private ParentChildLinkService parentChildLinkService;
  @Mock private PartyResolver partyResolver;
  @Mock private CompanyRoles companyRoles;

  @InjectMocks private SavingsFundAccountHolderResolver resolver;

  private static final UUID LINK_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
  private static final UUID COMPANY_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");

  private final User payer =
      User.builder().personalCode("38888888888").firstName("Parent").lastName("Tester").build();

  @Test
  void paymentForSelfResolvesToThePerson() {
    var resolved = resolver.resolve(payer, new PartyId(PERSON, payer.getPersonalCode()));

    assertThat(resolved).isEqualTo(SavingsFundAccountHolder.person());
    verifyNoInteractions(partyResolver);
  }

  @Test
  void paymentForRepresentedChildResolvesToTheChildRegardlessOfTheRepresentativesKycStatus() {
    String childCode = "51111111111";
    User child = User.builder().personalCode(childCode).firstName("Kid").lastName("Tester").build();
    given(
            parentChildLinkService.findRepresentation(
                payer.getPersonalCode(), childCode, Set.of(ACTIVE, PENDING_KYC)))
        .willReturn(Optional.of(LINK_ID));
    given(partyResolver.resolve(new PartyId(PERSON, childCode)))
        .willReturn(Optional.of(namedParty(child.code(), child.name())));

    var resolved = resolver.resolve(payer, new PartyId(PERSON, childCode));

    assertThat(resolved).isEqualTo(SavingsFundAccountHolder.child(child.name(), LINK_ID));
    verify(parentChildLinkService, never()).isActiveRepresentation(any(), any());
  }

  @Test
  void paymentForARepresentedChildWithoutAResolvableNameStillCarriesTheAccountId() {
    String childCode = "51111111111";
    given(
            parentChildLinkService.findRepresentation(
                payer.getPersonalCode(), childCode, Set.of(ACTIVE, PENDING_KYC)))
        .willReturn(Optional.of(LINK_ID));
    given(partyResolver.resolve(new PartyId(PERSON, childCode))).willReturn(Optional.empty());

    var resolved = resolver.resolve(payer, new PartyId(PERSON, childCode));

    assertThat(resolved).isEqualTo(SavingsFundAccountHolder.child(LINK_ID));
  }

  @Test
  void paymentForAnotherPersonWhoIsNotARepresentedChildResolvesToThePerson() {
    String otherCode = "49001010001";
    given(
            parentChildLinkService.findRepresentation(
                payer.getPersonalCode(), otherCode, Set.of(ACTIVE, PENDING_KYC)))
        .willReturn(Optional.empty());

    var resolved = resolver.resolve(payer, new PartyId(PERSON, otherCode));

    assertThat(resolved).isEqualTo(SavingsFundAccountHolder.person());
    verifyNoInteractions(partyResolver);
  }

  @Test
  void paymentForCompanyResolvesToTheCompanyWithNameAndAccountId() {
    String registryCode = "12345678";
    given(companyRoles.findCompany(registryCode))
        .willReturn(Optional.of(new CompanyRole(COMPANY_ID, registryCode, "Tuleva OÜ")));

    var resolved = resolver.resolve(payer, new PartyId(LEGAL_ENTITY, registryCode));

    assertThat(resolved).isEqualTo(SavingsFundAccountHolder.company("Tuleva OÜ", COMPANY_ID));
    verifyNoInteractions(parentChildLinkService, partyResolver);
  }

  @Test
  void companyPaymentForAnUnknownCompanyResolvesToACompanyWithoutNameOrAccountId() {
    String registryCode = "87654321";
    given(companyRoles.findCompany(registryCode)).willReturn(Optional.empty());

    var resolved = resolver.resolve(payer, new PartyId(LEGAL_ENTITY, registryCode));

    assertThat(resolved).isEqualTo(SavingsFundAccountHolder.companyNotOnFileYet());
  }

  @Test
  void retryForACompanyThePayerRepresentsOpensTheCompany() {
    String registryCode = "12345678";
    given(companyRoles.isBoardMember(payer.getPersonalCode(), registryCode)).willReturn(true);
    given(companyRoles.findCompany(registryCode))
        .willReturn(Optional.of(new CompanyRole(COMPANY_ID, registryCode, "Tuleva OÜ")));

    var resolved = resolver.resolveRetryAccount(payer, new PartyId(LEGAL_ENTITY, registryCode));

    assertThat(resolved).isEqualTo(SavingsFundAccountHolder.company("Tuleva OÜ", COMPANY_ID));
  }

  @Test
  void retryNamingACompanyThePayerDoesNotRepresentKeepsThePayersOwnLink() {
    String registryCode = "12345678";
    given(companyRoles.isBoardMember(payer.getPersonalCode(), registryCode)).willReturn(false);

    var resolved = resolver.resolveRetryAccount(payer, new PartyId(LEGAL_ENTITY, registryCode));

    assertThat(resolved).isEqualTo(SavingsFundAccountHolder.person());
    verify(companyRoles, never()).findCompany(any());
  }

  @Test
  void retryForARepresentedChildOpensTheChild() {
    String childCode = "51111111111";
    given(
            parentChildLinkService.findRepresentation(
                payer.getPersonalCode(), childCode, Set.of(ACTIVE, PENDING_KYC)))
        .willReturn(Optional.of(LINK_ID));
    given(partyResolver.resolve(new PartyId(PERSON, childCode))).willReturn(Optional.empty());

    var resolved = resolver.resolveRetryAccount(payer, new PartyId(PERSON, childCode));

    assertThat(resolved).isEqualTo(SavingsFundAccountHolder.child(LINK_ID));
    verifyNoInteractions(companyRoles);
  }

  @Test
  void retryForThePayersOwnAccountKeepsThePayersOwnLink() {
    var resolved =
        resolver.resolveRetryAccount(payer, new PartyId(PERSON, payer.getPersonalCode()));

    assertThat(resolved).isEqualTo(SavingsFundAccountHolder.person());
    verifyNoInteractions(companyRoles, parentChildLinkService, partyResolver);
  }

  private static Party namedParty(String code, String name) {
    record NamedParty(String code, String name) implements Party {}
    return new NamedParty(code, name);
  }
}
