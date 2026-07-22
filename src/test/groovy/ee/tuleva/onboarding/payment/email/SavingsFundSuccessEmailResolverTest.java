package ee.tuleva.onboarding.payment.email;

import static ee.tuleva.onboarding.party.PartyId.Type.LEGAL_ENTITY;
import static ee.tuleva.onboarding.party.PartyId.Type.PERSON;
import static java.util.Locale.ENGLISH;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

import ee.tuleva.onboarding.company.Company;
import ee.tuleva.onboarding.party.ParentChildLinkService;
import ee.tuleva.onboarding.party.PartyId;
import ee.tuleva.onboarding.party.PartyResolver;
import ee.tuleva.onboarding.payment.event.SavingsPaymentCreatedEvent;
import ee.tuleva.onboarding.user.User;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SavingsFundSuccessEmailResolverTest {

  @Mock private ParentChildLinkService parentChildLinkService;
  @Mock private PartyResolver partyResolver;

  @InjectMocks private SavingsFundSuccessEmailResolver resolver;

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
  void paymentForRepresentedChildResolvesToChildEmailWithChildName() {
    String childCode = "51111111111";
    User child = User.builder().personalCode(childCode).firstName("Kid").lastName("Tester").build();
    given(parentChildLinkService.isActiveRepresentation(payer.getPersonalCode(), childCode))
        .willReturn(true);
    given(partyResolver.resolve(new PartyId(PERSON, childCode))).willReturn(Optional.of(child));

    var resolved = resolver.resolve(event(new PartyId(PERSON, childCode)));

    assertThat(resolved).isEqualTo(SavingsFundPaymentEmail.childSuccess(child.name()));
  }

  @Test
  void paymentForAnotherPersonWhoIsNotARepresentedChildResolvesToPersonEmail() {
    String otherCode = "49001010001";
    given(parentChildLinkService.isActiveRepresentation(payer.getPersonalCode(), otherCode))
        .willReturn(false);

    var resolved = resolver.resolve(event(new PartyId(PERSON, otherCode)));

    assertThat(resolved).isEqualTo(SavingsFundPaymentEmail.personSuccess());
    verifyNoInteractions(partyResolver);
  }

  @Test
  void paymentForCompanyResolvesToCompanyEmailWithCompanyName() {
    String registryCode = "12345678";
    Company company = Company.builder().registryCode(registryCode).name("Tuleva OÜ").build();
    given(partyResolver.resolve(new PartyId(LEGAL_ENTITY, registryCode)))
        .willReturn(Optional.of(company));

    var resolved = resolver.resolve(event(new PartyId(LEGAL_ENTITY, registryCode)));

    assertThat(resolved).isEqualTo(SavingsFundPaymentEmail.companySuccess(company.name()));
    verifyNoInteractions(parentChildLinkService);
  }

  @Test
  void companyPaymentWithUnresolvablePartyResolvesToCompanyEmailWithoutName() {
    String registryCode = "87654321";
    given(partyResolver.resolve(new PartyId(LEGAL_ENTITY, registryCode)))
        .willReturn(Optional.empty());

    var resolved = resolver.resolve(event(new PartyId(LEGAL_ENTITY, registryCode)));

    assertThat(resolved).isEqualTo(SavingsFundPaymentEmail.companySuccess(null));
  }
}
