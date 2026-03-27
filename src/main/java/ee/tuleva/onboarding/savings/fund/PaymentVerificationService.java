package ee.tuleva.onboarding.savings.fund;

import static ee.tuleva.onboarding.party.PartyId.Type.LEGAL_ENTITY;
import static ee.tuleva.onboarding.party.PartyId.Type.PERSON;
import static ee.tuleva.onboarding.savings.fund.SavingFundPayment.Status.TO_BE_RETURNED;
import static ee.tuleva.onboarding.savings.fund.SavingFundPayment.Status.VERIFIED;

import ee.tuleva.onboarding.kyb.RegistryCodeValidator;
import ee.tuleva.onboarding.ledger.SavingsFundLedger;
import ee.tuleva.onboarding.party.PartyId;
import ee.tuleva.onboarding.party.PartyResolver;
import ee.tuleva.onboarding.payment.event.SavingsPaymentFailedEvent;
import ee.tuleva.onboarding.savings.fund.notification.UnattributedPaymentEvent;
import ee.tuleva.onboarding.user.UserRepository;
import ee.tuleva.onboarding.user.personalcode.PersonalCodeValidator;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
public class PaymentVerificationService {
  private static final PersonalCodeValidator personalCodeValidator = new PersonalCodeValidator();
  private static final RegistryCodeValidator registryCodeValidator = new RegistryCodeValidator();
  private static final ZoneId ESTONIAN_ZONE = ZoneId.of("Europe/Tallinn");

  private final SavingFundPaymentRepository savingFundPaymentRepository;
  private final UserRepository userRepository;
  private final SavingsFundOnboardingService savingsFundOnboardingService;
  private final SavingsFundLedger savingsFundLedger;
  private final ApplicationEventPublisher applicationEventPublisher;
  private final NameMatcher nameMatcher;
  private final PartyResolver partyResolver;

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
      return BY_TYPE.get(type);
    }
  }

  @Transactional
  public void process(SavingFundPayment payment) {
    log.info("Processing payment {}", payment.getId());

    var extractPartyId = extractPartyId(payment.getDescription());
    if (extractPartyId.isEmpty()) {
      identityCheckFailure(payment, "selgituses puudub isikukood/registrikood");
      return;
    }

    PartyId partyId = extractPartyId.get();
    var messages = VerificationMessages.forType(partyId.type());

    var remitterPartyId = extractPartyId(payment.getRemitterIdCode());
    if (remitterPartyId.isPresent() && !remitterPartyId.get().equals(partyId)) {
      identityCheckFailure(payment, messages.codeMismatch());
      return;
    }

    var party = partyResolver.resolve(partyId);
    if (party.isEmpty()) {
      identityCheckFailure(payment, messages.notClient());
      return;
    }

    if (remitterPartyId.isEmpty()
        && !nameMatcher.isSameName(party.get().name(), payment.getRemitterName())) {
      identityCheckFailure(payment, messages.nameMismatch());
      return;
    }

    if (!savingsFundOnboardingService.isOnboardingCompleted(party.get().code())) {
      identityCheckFailure(payment, messages.notOnboarded());
      return;
    }

    savingFundPaymentRepository.attachParty(payment.getId(), partyId);

    log.info(
        "Verification completed for payment {}, attaching to party {}", payment.getId(), partyId);
    savingFundPaymentRepository.changeStatus(payment.getId(), VERIFIED);
    savingsFundLedger.recordPaymentReceived(
        partyId, payment.getAmount(), payment.getId(), bookingDate(payment));
  }

  private void identityCheckFailure(SavingFundPayment payment, String reason) {
    log.info("Identity check failed for payment {}: {}", payment.getId(), reason);
    savingFundPaymentRepository.changeStatus(payment.getId(), TO_BE_RETURNED);
    savingFundPaymentRepository.addReturnReason(payment.getId(), reason);

    savingsFundLedger.recordUnattributedPayment(
        payment.getAmount(), payment.getId(), bookingDate(payment));

    applicationEventPublisher.publishEvent(
        new UnattributedPaymentEvent(payment.getId(), payment.getAmount(), reason));

    Optional.ofNullable(payment.getPartyId())
        .map(PartyId::code)
        .flatMap(userRepository::findByPersonalCode)
        .ifPresent(
            user ->
                applicationEventPublisher.publishEvent(
                    new SavingsPaymentFailedEvent(this, user, Locale.of("et"))));
  }

  private static LocalDate bookingDate(SavingFundPayment payment) {
    return payment.getReceivedBefore().atZone(ESTONIAN_ZONE).toLocalDate();
  }

  Optional<PartyId> extractPartyId(String text) {
    return extractPersonalCode(text)
        .map(code -> new PartyId(PERSON, code))
        .or(() -> extractRegistryCode(text).map(code -> new PartyId(LEGAL_ENTITY, code)));
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
