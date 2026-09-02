package ee.tuleva.onboarding.payment.email;

import static ee.tuleva.onboarding.party.ParentChildLinkStatus.ACTIVE;
import static ee.tuleva.onboarding.party.ParentChildLinkStatus.PENDING_KYC;
import static ee.tuleva.onboarding.party.PartyId.Type.LEGAL_ENTITY;
import static ee.tuleva.onboarding.party.PartyId.Type.PERSON;
import static java.util.Locale.ENGLISH;
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
import ee.tuleva.onboarding.payment.event.SavingsPaymentCreatedEvent;
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
class SavingsFundSuccessEmailResolverTest {

  @Mock private ParentChildLinkService parentChildLinkService;
  @Mock private PartyResolver partyResolver;
  @Mock private CompanyRoles companyRoles;

  @InjectMocks private SavingsFundSuccessEmailResolver resolver;

  private static final UUID LINK_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
  private static final UUID COMPANY_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");

  private final User payer =
      User.builder().personalCode("38888888888").firstName("Parent").lastName("Tester").build();

  private SavingsPaymentCreatedEvent event(PartyId recipient) {
    return new SavingsPaymentCreatedEvent(this, payer, ENGLISH, recipient);
  }

  @Test
  void paymentForSelfResolvesToPersonEmail() {
    var resolved = resolver.resolve(event(new PartyId(PERSON, payer.getPersonalCode())));

    assertThat(resolved).isEqualTo(SavingsFundPaymentEmail.personSuccess());
    verifyNoInteractions(partyResolver);
  }

  @Test
  void paymentForRepresentedChildResolvesToChildEmailRegardlessOfTheRepresentativesKycStatus() {
    String childCode = "51111111111";
    User child = User.builder().personalCode(childCode).firstName("Kid").lastName("Tester").build();
    given(
            parentChildLinkService.findRepresentation(
                payer.getPersonalCode(), childCode, Set.of(ACTIVE, PENDING_KYC)))
        .willReturn(Optional.of(LINK_ID));
    given(partyResolver.resolve(new PartyId(PERSON, childCode)))
        .willReturn(Optional.of(namedParty(child.code(), child.name())));

    var resolved = resolver.resolve(event(new PartyId(PERSON, childCode)));

    assertThat(resolved).isEqualTo(SavingsFundPaymentEmail.childSuccess(child.name(), LINK_ID));
    verify(parentChildLinkService, never()).isActiveRepresentation(any(), any());
  }

  @Test
  void paymentForAnotherPersonWhoIsNotARepresentedChildResolvesToPersonEmail() {
    String otherCode = "49001010001";
    given(
            parentChildLinkService.findRepresentation(
                payer.getPersonalCode(), otherCode, Set.of(ACTIVE, PENDING_KYC)))
        .willReturn(Optional.empty());

    var resolved = resolver.resolve(event(new PartyId(PERSON, otherCode)));

    assertThat(resolved).isEqualTo(SavingsFundPaymentEmail.personSuccess());
    verifyNoInteractions(partyResolver);
  }

  @Test
  void paymentForCompanyResolvesToCompanyEmailWithNameAndAccountId() {
    String registryCode = "12345678";
    given(companyRoles.findCompany(registryCode))
        .willReturn(Optional.of(new CompanyRole(COMPANY_ID, registryCode, "Tuleva OÜ")));

    var resolved = resolver.resolve(event(new PartyId(LEGAL_ENTITY, registryCode)));

    assertThat(resolved).isEqualTo(SavingsFundPaymentEmail.companySuccess("Tuleva OÜ", COMPANY_ID));
    verifyNoInteractions(parentChildLinkService, partyResolver);
  }

  @Test
  void companyPaymentForAnUnknownCompanyResolvesToTheGenericPersonEmail() {
    String registryCode = "87654321";
    given(companyRoles.findCompany(registryCode)).willReturn(Optional.empty());

    var resolved = resolver.resolve(event(new PartyId(LEGAL_ENTITY, registryCode)));

    assertThat(resolved).isEqualTo(SavingsFundPaymentEmail.personSuccess());
  }

  private static Party namedParty(String code, String name) {
    record NamedParty(String code, String name) implements Party {}
    return new NamedParty(code, name);
  }
}
