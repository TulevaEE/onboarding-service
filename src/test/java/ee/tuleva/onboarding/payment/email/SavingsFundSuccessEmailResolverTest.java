package ee.tuleva.onboarding.payment.email;

import static ee.tuleva.onboarding.mandate.email.persistence.EmailType.SAVINGS_FUND_PAYMENT_SUCCESS_CHILD;
import static ee.tuleva.onboarding.mandate.email.persistence.EmailType.SAVINGS_FUND_PAYMENT_SUCCESS_COMPANY;
import static ee.tuleva.onboarding.mandate.email.persistence.EmailType.SAVINGS_FUND_PAYMENT_SUCCESS_PERSON;
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
import ee.tuleva.onboarding.payment.email.SavingsFundSuccessEmailResolver.ResolvedEmail;
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
      User.builder().personalCode("38812121215").firstName("Jordan").lastName("Valdma").build();

  private SavingsPaymentCreatedEvent event(String recipientCode, PartyId.Type recipientType) {
    return new SavingsPaymentCreatedEvent(this, payer, ENGLISH, recipientCode, recipientType);
  }

  @Test
  void paymentForSelfResolvesToPersonTemplateWithoutRecipientName() {
    ResolvedEmail resolved = resolver.resolve(payer, event(payer.getPersonalCode(), PERSON));

    assertThat(resolved).isEqualTo(new ResolvedEmail(SAVINGS_FUND_PAYMENT_SUCCESS_PERSON, null));
    verifyNoInteractions(partyResolver);
  }

  @Test
  void paymentWithoutRecipientCodeResolvesToPersonTemplate() {
    ResolvedEmail resolved = resolver.resolve(payer, event(null, PERSON));

    assertThat(resolved).isEqualTo(new ResolvedEmail(SAVINGS_FUND_PAYMENT_SUCCESS_PERSON, null));
    verifyNoInteractions(partyResolver);
  }

  @Test
  void paymentForRepresentedChildResolvesToChildTemplateWithChildName() {
    String childCode = "51107121760";
    User child = User.builder().personalCode(childCode).firstName("Kid").lastName("Valdma").build();
    given(parentChildLinkService.isActiveRepresentation(payer.getPersonalCode(), childCode))
        .willReturn(true);
    given(partyResolver.resolve(new PartyId(PERSON, childCode))).willReturn(Optional.of(child));

    ResolvedEmail resolved = resolver.resolve(payer, event(childCode, PERSON));

    assertThat(resolved)
        .isEqualTo(new ResolvedEmail(SAVINGS_FUND_PAYMENT_SUCCESS_CHILD, child.name()));
  }

  @Test
  void paymentForAnotherPersonWhoIsNotARepresentedChildFallsBackToPersonTemplate() {
    String otherCode = "49001010001";
    given(parentChildLinkService.isActiveRepresentation(payer.getPersonalCode(), otherCode))
        .willReturn(false);

    ResolvedEmail resolved = resolver.resolve(payer, event(otherCode, PERSON));

    assertThat(resolved).isEqualTo(new ResolvedEmail(SAVINGS_FUND_PAYMENT_SUCCESS_PERSON, null));
    verifyNoInteractions(partyResolver);
  }

  @Test
  void paymentForCompanyResolvesToCompanyTemplateWithCompanyName() {
    String registryCode = "12345678";
    Company company = Company.builder().registryCode(registryCode).name("Tuleva OÜ").build();
    given(partyResolver.resolve(new PartyId(LEGAL_ENTITY, registryCode)))
        .willReturn(Optional.of(company));

    ResolvedEmail resolved = resolver.resolve(payer, event(registryCode, LEGAL_ENTITY));

    assertThat(resolved)
        .isEqualTo(new ResolvedEmail(SAVINGS_FUND_PAYMENT_SUCCESS_COMPANY, company.name()));
    verifyNoInteractions(parentChildLinkService);
  }

  @Test
  void companyPaymentWithUnresolvablePartyStillResolvesToCompanyTemplateWithNullName() {
    String registryCode = "87654321";
    given(partyResolver.resolve(new PartyId(LEGAL_ENTITY, registryCode)))
        .willReturn(Optional.empty());

    ResolvedEmail resolved = resolver.resolve(payer, event(registryCode, LEGAL_ENTITY));

    assertThat(resolved).isEqualTo(new ResolvedEmail(SAVINGS_FUND_PAYMENT_SUCCESS_COMPANY, null));
  }
}
