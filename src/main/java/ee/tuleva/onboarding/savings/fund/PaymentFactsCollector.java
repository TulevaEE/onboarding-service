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

  record Collected(Payment facts, Optional<Screening> payerNameScreening) {}

  private record UncodedPayer(Kind kind, Screening screening) {

    enum Kind {
      CLIENT,
      THIRD_PARTY,
      NOT_CHECKED
    }

    static final UncodedPayer NOT_CHECKED =
        new UncodedPayer(Kind.NOT_CHECKED, Screening.NOT_CHECKED);

    Optional<Screening> nameScreening() {
      return kind == Kind.THIRD_PARTY && screening != Screening.UNAVAILABLE
          ? Optional.of(screening)
          : Optional.empty();
    }
  }

  Collected collect(
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
    var partyResolved = !alreadyDecided(facts);
    Optional<Party> party = partyResolved ? partyResolver.resolve(partyId) : Optional.empty();
    facts = withPartyAndOnboarding(facts, party, partyId, payment);
    var unitHolderName = party.map(Party::name).orElse(payment.getRemitterName());
    var uncodedPayer = UncodedPayer.NOT_CHECKED;
    if (remitterPartyId.isPresent()) {
      var payerName = facts.remitter() == HOLDER ? unitHolderName : payment.getRemitterName();
      facts =
          facts.withPayerScreening(paymentPartyScreener.screen(remitterPartyId.get(), payerName));
    } else {
      var client = partyResolved ? party : partyResolver.resolve(partyId);
      uncodedPayer = uncodedPayer(payment.getRemitterName(), partyId, client);
      facts = facts.withPayerScreening(uncodedPayer.screening());
    }
    if (alreadyDecided(facts)
        || facts.remitter() == HOLDER
        || uncodedPayer.kind() == UncodedPayer.Kind.CLIENT) {
      return new Collected(facts, uncodedPayer.nameScreening());
    }
    return new Collected(
        facts.withScreening(paymentPartyScreener.screen(partyId, unitHolderName)),
        uncodedPayer.nameScreening());
  }

  private UncodedPayer uncodedPayer(
      @Nullable String payerName, PartyId partyId, Optional<Party> client) {
    if (payerName == null || payerName.isBlank()) {
      return UncodedPayer.NOT_CHECKED;
    }
    return client
        .filter(theClient -> nameMatcher.isSimilarName(payerName, theClient.name()))
        .map(
            theClient ->
                new UncodedPayer(
                    UncodedPayer.Kind.CLIENT,
                    paymentPartyScreener.screen(partyId, theClient.name())))
        .orElseGet(
            () ->
                new UncodedPayer(
                    UncodedPayer.Kind.THIRD_PARTY, paymentPartyScreener.screenByName(payerName)));
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
