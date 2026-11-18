package ee.tuleva.onboarding.savings.fund;

import static ee.tuleva.onboarding.party.PartyId.Type.LEGAL_ENTITY;
import static ee.tuleva.onboarding.party.PartyId.Type.PERSON;
import static ee.tuleva.onboarding.savings.fund.PaymentAcceptance.Client.KNOWN;
import static ee.tuleva.onboarding.savings.fund.PaymentAcceptance.Client.UNKNOWN;
import static ee.tuleva.onboarding.savings.fund.PaymentAcceptance.Code.FOUND;
import static ee.tuleva.onboarding.savings.fund.PaymentAcceptance.Minor.NAMED_IN_DESCRIPTION;
import static ee.tuleva.onboarding.savings.fund.PaymentAcceptance.Minor.NOT_NAMED;
import static ee.tuleva.onboarding.savings.fund.PaymentAcceptance.Name.DIFFERS;
import static ee.tuleva.onboarding.savings.fund.PaymentAcceptance.Name.MATCHES;
import static ee.tuleva.onboarding.savings.fund.PaymentAcceptance.Onboarding.COMPLETED;
import static ee.tuleva.onboarding.savings.fund.PaymentAcceptance.Onboarding.INCOMPLETE;
import static ee.tuleva.onboarding.savings.fund.PaymentAcceptance.Outcome.VERIFY;
import static ee.tuleva.onboarding.savings.fund.PaymentAcceptance.Remitter.ANOTHER_COMPANY;
import static ee.tuleva.onboarding.savings.fund.PaymentAcceptance.Remitter.ANOTHER_PERSON;
import static ee.tuleva.onboarding.savings.fund.PaymentAcceptance.Remitter.COMPANY_NAME_WITHOUT_CODE;
import static ee.tuleva.onboarding.savings.fund.PaymentAcceptance.Remitter.GUARDIAN;
import static ee.tuleva.onboarding.savings.fund.PaymentAcceptance.Remitter.HOLDER;
import static ee.tuleva.onboarding.savings.fund.PaymentAcceptance.Remitter.NO_CODE;
import static ee.tuleva.onboarding.savings.fund.PaymentAcceptance.outcome;

import ee.tuleva.onboarding.party.ParentChildLinkService;
import ee.tuleva.onboarding.party.Party;
import ee.tuleva.onboarding.party.PartyId;
import ee.tuleva.onboarding.party.PartyResolver;
import ee.tuleva.onboarding.savings.SavingFundPayment;
import ee.tuleva.onboarding.savings.SavingsFundOnboardingService;
import ee.tuleva.onboarding.savings.fund.PaymentAcceptance.Client;
import ee.tuleva.onboarding.savings.fund.PaymentAcceptance.Minor;
import ee.tuleva.onboarding.savings.fund.PaymentAcceptance.Name;
import ee.tuleva.onboarding.savings.fund.PaymentAcceptance.Payment;
import ee.tuleva.onboarding.savings.fund.PaymentAcceptance.Remitter;
import ee.tuleva.onboarding.savings.fund.PaymentAcceptance.Screening;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class PaymentFactsCollector {

  private final SavingsFundOnboardingService savingsFundOnboardingService;
  private final NameMatcher nameMatcher;
  private final PartyResolver partyResolver;
  private final ParentChildLinkService parentChildLinkService;
  private final PaymentPartyScreener paymentPartyScreener;

  Payment collect(
      SavingFundPayment payment,
      PartyId partyId,
      Optional<PartyId> partyIdFromDescription,
      Optional<PartyId> remitterPartyId) {
    var facts =
        Payment.identified(FOUND)
            .withRemitter(remitter(remitterPartyId, payment.getRemitterName(), partyId));
    if (!alreadyDecided(facts)) {
      facts = facts.withMinor(minor(partyIdFromDescription, partyId, payment));
    }
    Optional<Party> party =
        alreadyDecided(facts) ? Optional.empty() : partyResolver.resolve(partyId);
    facts = withPartyAndOnboarding(facts, party, partyId, payment);
    var unitHolderName = party.map(Party::name).orElse(payment.getRemitterName());
    var payerName = facts.remitter() == HOLDER ? unitHolderName : payment.getRemitterName();
    facts = facts.withPayerScreening(payerScreening(remitterPartyId, payerName));
    if (alreadyDecided(facts) || facts.remitter() == HOLDER) {
      return facts;
    }
    return facts.withScreening(paymentPartyScreener.screen(partyId, unitHolderName));
  }

  private Payment withPartyAndOnboarding(
      Payment facts, Optional<Party> party, PartyId partyId, SavingFundPayment payment) {
    if (alreadyDecided(facts)) {
      return facts;
    }
    var withParty =
        facts.withParty(client(party), party.map(p -> name(p, payment)).orElse(Name.NOT_CHECKED));
    if (alreadyDecided(withParty)) {
      return withParty;
    }
    return withParty.withOnboarding(
        savingsFundOnboardingService.isOnboardingCompleted(partyId) ? COMPLETED : INCOMPLETE);
  }

  private Screening payerScreening(Optional<PartyId> remitterPartyId, @Nullable String payerName) {
    return remitterPartyId
        .map(payer -> paymentPartyScreener.screen(payer, payerName))
        .orElse(Screening.NOT_CHECKED);
  }

  private static boolean alreadyDecided(Payment facts) {
    return outcome(facts) != VERIFY;
  }

  private Remitter remitter(
      Optional<PartyId> remitterPartyId, @Nullable String remitterName, PartyId partyId) {
    return remitterPartyId
        .map(remitter -> remitterWithCode(remitter, partyId))
        .orElseGet(
            () ->
                partyId.type() == PERSON && nameMatcher.looksLikeACompany(remitterName)
                    ? COMPANY_NAME_WITHOUT_CODE
                    : NO_CODE);
  }

  private Remitter remitterWithCode(PartyId remitter, PartyId partyId) {
    if (remitter.equals(partyId)) {
      return HOLDER;
    }
    if (remitter.type() == LEGAL_ENTITY) {
      return ANOTHER_COMPANY;
    }
    return isGuardianOf(remitter, partyId) ? GUARDIAN : ANOTHER_PERSON;
  }

  private boolean isGuardianOf(PartyId remitter, PartyId party) {
    return party.type() == PERSON
        && parentChildLinkService.isGuardian(remitter.code(), party.code());
  }

  private Minor minor(
      Optional<PartyId> partyIdFromDescription, PartyId partyId, SavingFundPayment payment) {
    return partyIdFromDescription.isPresent()
            && partyId.type() == PERSON
            && parentChildLinkService.hasRestrictedLegalCapacity(
                partyId.code(), payment.bookingDateOrThrow())
        ? NAMED_IN_DESCRIPTION
        : NOT_NAMED;
  }

  private static Client client(Optional<Party> party) {
    return party.isPresent() ? KNOWN : UNKNOWN;
  }

  private Name name(Party party, SavingFundPayment payment) {
    return nameMatcher.isSameName(party.name(), payment.getRemitterName()) ? MATCHES : DIFFERS;
  }
}
