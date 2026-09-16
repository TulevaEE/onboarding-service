package ee.tuleva.onboarding.savings.fund;

import static ee.tuleva.onboarding.party.ParentChildLinkStatus.ACTIVE;
import static ee.tuleva.onboarding.party.ParentChildLinkStatus.PENDING_KYC;
import static ee.tuleva.onboarding.party.PartyId.Type.LEGAL_ENTITY;
import static ee.tuleva.onboarding.party.PartyId.Type.PERSON;
import static ee.tuleva.onboarding.savings.SavingFundPayment.Status.TO_BE_RETURNED;
import static ee.tuleva.onboarding.savings.SavingFundPayment.Status.VERIFIED;

import ee.tuleva.onboarding.event.TrackableEventType;
import ee.tuleva.onboarding.event.TrackableSystemEvent;
import ee.tuleva.onboarding.kyb.RegistryCodeValidator;
import ee.tuleva.onboarding.ledger.SavingsFundLedger;
import ee.tuleva.onboarding.party.ParentChildLinkService;
import ee.tuleva.onboarding.party.Party;
import ee.tuleva.onboarding.party.PartyId;
import ee.tuleva.onboarding.party.PartyResolver;
import ee.tuleva.onboarding.payment.event.SavingsPaymentFailedEvent;
import ee.tuleva.onboarding.personalcode.PersonalCodeValidator;
import ee.tuleva.onboarding.savings.SavingFundPayment;
import ee.tuleva.onboarding.savings.SavingsFundOnboardingService;
import ee.tuleva.onboarding.savings.fund.notification.UnattributedPaymentEvent;
import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.user.UserRepository;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
public class PaymentVerificationService {
  private static final PersonalCodeValidator personalCodeValidator = new PersonalCodeValidator();
  private static final RegistryCodeValidator registryCodeValidator = new RegistryCodeValidator();

  private final SavingFundPaymentRepository savingFundPaymentRepository;
  private final UserRepository userRepository;
  private final SavingsFundOnboardingService savingsFundOnboardingService;
  private final SavingsFundLedger savingsFundLedger;
  private final ApplicationEventPublisher applicationEventPublisher;
  private final NameMatcher nameMatcher;
  private final PartyResolver partyResolver;
  private final ParentChildLinkService parentChildLinkService;

  record VerificationMessages(
      String codeMismatch, String notClient, String nameMismatch, String notOnboarded) {
    private static final Map<PartyId.Type, VerificationMessages> BY_TYPE =
        Map.of(
            PERSON,
            new VerificationMessages(
                "selgituses olev isikukood ei klapi maksja isikukoodiga",
                "isik ei ole Tuleva klient",
                "maksja nimi ei klapi Tuleva andmetega",
                "see isik ei ole täiendava kogumisfondiga liitunud"),
            LEGAL_ENTITY,
            new VerificationMessages(
                "selgituses olev registrikood ei klapi maksja registrikoodiga",
                "ettevõte ei ole Tuleva klient",
                "maksja nimi ei klapi Tuleva andmetega",
                "see ettevõte ei ole täiendava kogumisfondiga liitunud"));

    static VerificationMessages forType(PartyId.Type type) {
      return Objects.requireNonNull(
          BY_TYPE.get(type), "Missing verification messages: type=" + type);
    }
  }

  @Transactional
  public void process(SavingFundPayment payment) {
    log.info("Processing payment {}", payment.getId());

    var partyIdOpt = extractPartyId(payment);
    if (partyIdOpt.isEmpty()) {
      identityCheckFailure(payment, "makse ei sisalda tuvastatavat isikukoodi/registrikoodi");
      return;
    }

    PartyId partyId = partyIdOpt.get();
    var messages = VerificationMessages.forType(partyId.type());

    var remitterPartyId = parsePartyId(payment.getRemitterIdCode());
    boolean representingChild =
        remitterPartyId
            .filter(r -> !r.equals(partyId))
            .map(r -> isAuthorizedRemitter(r, partyId))
            .orElse(false);
    if (remitterPartyId.isPresent()
        && !remitterPartyId.get().equals(partyId)
        && !representingChild) {
      identityCheckFailure(payment, messages.codeMismatch());
      return;
    }

    Optional<Party> party = partyResolver.resolve(partyId);
    if (party.isEmpty()) {
      identityCheckFailure(payment, messages.notClient());
      return;
    }

    if (remitterPartyId.isEmpty()
        && !nameMatcher.isSameName(party.get().name(), payment.getRemitterName())) {
      identityCheckFailure(payment, messages.nameMismatch());
      return;
    }

    if (!savingsFundOnboardingService.isOnboardingCompleted(partyId)) {
      identityCheckFailure(payment, messages.notOnboarded());
      return;
    }

    savingFundPaymentRepository.attachParty(payment.getId(), partyId);

    log.info(
        "Verification completed for payment {}, attaching to party {}", payment.getId(), partyId);
    savingFundPaymentRepository.changeStatus(payment.getId(), VERIFIED);
    savingsFundLedger.recordPaymentReceived(
        LedgerRefs.from(partyId),
        payment.getAmount(),
        payment.getId(),
        Objects.requireNonNull(
            payment.bookingDate(), "Missing receivedBefore: paymentId=" + payment.getId()));

    if (representingChild) {
      applicationEventPublisher.publishEvent(
          new TrackableSystemEvent(
              TrackableEventType.MINOR_DEPOSIT_VERIFIED,
              Map.of(
                  "parentPersonalCode", remitterPartyId.get().code(),
                  "childPersonalCode", partyId.code(),
                  "paymentId", payment.getId(),
                  "amount", payment.getAmount())));
    }
  }

  private void identityCheckFailure(SavingFundPayment payment, String reason) {
    log.info("Identity check failed for payment {}: {}", payment.getId(), reason);
    savingFundPaymentRepository.changeStatus(payment.getId(), TO_BE_RETURNED);
    savingFundPaymentRepository.addReturnReason(payment.getId(), reason);

    savingsFundLedger.recordUnattributedPayment(
        payment.getAmount(),
        payment.getId(),
        Objects.requireNonNull(
            payment.bookingDate(), "Missing receivedBefore: paymentId=" + payment.getId()));

    applicationEventPublisher.publishEvent(
        new UnattributedPaymentEvent(payment.getId(), payment.getAmount(), reason));

    findPayer(payment)
        .ifPresent(
            user ->
                applicationEventPublisher.publishEvent(
                    new SavingsPaymentFailedEvent(this, user, Locale.of("et"))));
  }

  private Optional<User> findPayer(SavingFundPayment payment) {
    var remitterIdCode = payment.getRemitterIdCode();
    if (remitterIdCode != null) {
      return userRepository.findByPersonalCode(remitterIdCode);
    }
    return Optional.ofNullable(payment.getPartyId())
        .map(PartyId::code)
        .flatMap(userRepository::findByPersonalCode);
  }

  private Optional<PartyId> extractPartyId(SavingFundPayment payment) {
    var partyIdFromDescription = extractPartyIdFromDescription(payment.getDescription());
    if (partyIdFromDescription.isPresent()) {
      return partyIdFromDescription;
    }
    log.info(
        "Payment {} has no code in description, falling back to remitter id code", payment.getId());
    return parsePartyId(payment.getRemitterIdCode());
  }

  // Attribution, not authorization: deciding whether an incoming payment is plausibly for this
  // child, so we attribute it instead of bouncing it. Accepting money grants the payer no access —
  // acting on the child's behalf goes through isActiveRepresentation. Tuleva intends to accept
  // third-party payments generally, at which point this widening goes away.
  private boolean isAuthorizedRemitter(PartyId remitter, PartyId party) {
    return remitter.type() == PERSON
        && party.type() == PERSON
        && parentChildLinkService
            .findRepresentation(remitter.code(), party.code(), Set.of(ACTIVE, PENDING_KYC))
            .isPresent();
  }

  Optional<PartyId> extractPartyIdFromDescription(String text) {
    return extractPersonalCode(text)
        .map(code -> new PartyId(PERSON, code))
        .or(() -> extractRegistryCode(text).map(code -> new PartyId(LEGAL_ENTITY, code)));
  }

  Optional<PartyId> parsePartyId(@Nullable String idCode) {
    if (idCode == null) return Optional.empty();
    if (personalCodeValidator.isValid(idCode)) return Optional.of(new PartyId(PERSON, idCode));
    if (registryCodeValidator.isValid(idCode))
      return Optional.of(new PartyId(LEGAL_ENTITY, idCode));
    return Optional.empty();
  }

  private Optional<String> extractPersonalCode(String text) {
    if (text == null) return Optional.empty();
    var matcher = Pattern.compile("(?<![0-9])[0-9]{11}(?![0-9])").matcher(text);
    if (!matcher.find()) return Optional.empty();
    var possiblePersonalCode = matcher.group();
    if (personalCodeValidator.isValid(possiblePersonalCode))
      return Optional.of(possiblePersonalCode);
    return Optional.empty();
  }

  private Optional<String> extractRegistryCode(String text) {
    if (text == null) return Optional.empty();
    var matcher = Pattern.compile("(?<![0-9])[0-9]{8}(?![0-9])").matcher(text);
    if (!matcher.find()) return Optional.empty();
    var possibleRegistryCode = matcher.group();
    if (registryCodeValidator.isValid(possibleRegistryCode))
      return Optional.of(possibleRegistryCode);
    return Optional.empty();
  }
}
