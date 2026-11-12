package ee.tuleva.onboarding.savings.fund;

import static ee.tuleva.onboarding.party.PartyId.Type.LEGAL_ENTITY;
import static ee.tuleva.onboarding.party.PartyId.Type.PERSON;
import static ee.tuleva.onboarding.savings.SavingFundPayment.Status.TO_BE_RETURNED;
import static ee.tuleva.onboarding.savings.SavingFundPayment.Status.VERIFIED;
import static ee.tuleva.onboarding.savings.fund.PaymentAcceptance.Code.FOUND;
import static ee.tuleva.onboarding.savings.fund.PaymentAcceptance.Code.MISSING;
import static ee.tuleva.onboarding.savings.fund.PaymentAcceptance.Name.MATCHES;
import static ee.tuleva.onboarding.savings.fund.PaymentAcceptance.Outcome.RETURN_UNIDENTIFIED;
import static ee.tuleva.onboarding.savings.fund.PaymentAcceptance.Outcome.VERIFY;
import static ee.tuleva.onboarding.savings.fund.PaymentAcceptance.Remitter.ANOTHER_COMPANY;
import static ee.tuleva.onboarding.savings.fund.PaymentAcceptance.Remitter.ANOTHER_PERSON;
import static ee.tuleva.onboarding.savings.fund.PaymentAcceptance.Remitter.COMPANY_NAME_WITHOUT_CODE;
import static ee.tuleva.onboarding.savings.fund.PaymentAcceptance.Remitter.GUARDIAN;
import static ee.tuleva.onboarding.savings.fund.PaymentAcceptance.Remitter.HOLDER;
import static ee.tuleva.onboarding.savings.fund.PaymentAcceptance.Remitter.NO_CODE;
import static ee.tuleva.onboarding.savings.fund.PaymentAcceptance.SISEKORD_16_P_1_2_4;

import ee.tuleva.onboarding.event.TrackableEventType;
import ee.tuleva.onboarding.event.TrackableSystemEvent;
import ee.tuleva.onboarding.kyb.RegistryCodeValidator;
import ee.tuleva.onboarding.ledger.SavingsFundLedger;
import ee.tuleva.onboarding.party.PartyId;
import ee.tuleva.onboarding.payment.event.SavingsPaymentFailedEvent;
import ee.tuleva.onboarding.personalcode.PersonalCodeValidator;
import ee.tuleva.onboarding.savings.SavingFundPayment;
import ee.tuleva.onboarding.savings.fund.PaymentAcceptance.Outcome;
import ee.tuleva.onboarding.savings.fund.PaymentAcceptance.Payment;
import ee.tuleva.onboarding.savings.fund.notification.UnattributedPaymentEvent;
import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.user.UserRepository;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
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
  private static final String COMPANY_CAN_ONLY_PAY_INTO_ITS_OWN_ACCOUNT =
      "ettevõte saab teha sissemakseid ainult enda kontole";
  private static final String UNIDENTIFIED =
      "makse ei sisalda tuvastatavat isikukoodi/registrikoodi";

  private final SavingFundPaymentRepository savingFundPaymentRepository;
  private final UserRepository userRepository;
  private final SavingsFundLedger savingsFundLedger;
  private final ApplicationEventPublisher applicationEventPublisher;
  private final PaymentFactsCollector paymentFactsCollector;

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

    Optional<String> reason(Outcome outcome) {
      return switch (outcome) {
        case RETURN_UNIDENTIFIED -> Optional.of(UNIDENTIFIED);
        case RETURN_COMPANY_PAYING_FOR_SOMEONE_ELSE ->
            Optional.of(COMPANY_CAN_ONLY_PAY_INTO_ITS_OWN_ACCOUNT);
        case RETURN_CODE_MISMATCH -> Optional.of(codeMismatch);
        case RETURN_NOT_A_CLIENT -> Optional.of(notClient);
        case RETURN_NAME_MISMATCH -> Optional.of(nameMismatch);
        case RETURN_NOT_ONBOARDED -> Optional.of(notOnboarded);
        case VERIFY -> Optional.empty();
      };
    }
  }

  @Transactional
  public void process(SavingFundPayment payment) {
    log.info("Processing payment {}", payment.getId());

    var partyIdFromDescription = extractPartyIdFromDescription(payment.getDescription());
    var remitterPartyId = parsePartyId(payment.getRemitterIdCode());
    if (partyIdFromDescription.isEmpty()) {
      log.info(
          "Payment {} has no code in description, falling back to remitter id code",
          payment.getId());
    }
    var partyIdOpt = partyIdFromDescription.or(() -> remitterPartyId);
    if (SISEKORD_16_P_1_2_4.decide(Payment.identified(partyIdOpt.isPresent() ? FOUND : MISSING))
        == RETURN_UNIDENTIFIED) {
      identityCheckFailure(payment, UNIDENTIFIED);
      return;
    }

    PartyId partyId = partyIdOpt.orElseThrow();
    var facts =
        paymentFactsCollector.collect(payment, partyId, partyIdFromDescription, remitterPartyId);
    if (returned(payment, partyId, facts)) {
      return;
    }

    verify(payment, partyId, remitterPartyId, facts);
  }

  private boolean returned(SavingFundPayment payment, PartyId partyId, Payment facts) {
    var outcome = SISEKORD_16_P_1_2_4.decide(facts);
    VerificationMessages.forType(partyId.type())
        .reason(outcome)
        .ifPresent(reason -> identityCheckFailure(payment, partyId, reason));
    return outcome != VERIFY;
  }

  private void verify(
      SavingFundPayment payment,
      PartyId partyId,
      Optional<PartyId> remitterPartyId,
      Payment facts) {
    savingFundPaymentRepository.attachParty(payment.getId(), partyId, thirdPartyDeposit(facts));

    log.info(
        "Verification completed for payment {}, attaching to party {}", payment.getId(), partyId);
    savingFundPaymentRepository.changeStatus(payment.getId(), VERIFIED);
    savingsFundLedger.recordPaymentReceived(
        LedgerRefs.from(partyId),
        payment.getAmount(),
        payment.getId(),
        Objects.requireNonNull(
            payment.bookingDate(), "Missing receivedBefore: paymentId=" + payment.getId()));

    if (facts.remitter() == GUARDIAN) {
      applicationEventPublisher.publishEvent(
          new TrackableSystemEvent(
              TrackableEventType.MINOR_DEPOSIT_VERIFIED,
              Map.of(
                  "parentPersonalCode", remitterPartyId.orElseThrow().code(),
                  "childPersonalCode", partyId.code(),
                  "paymentId", payment.getId(),
                  "amount", payment.getAmount())));
    }
  }

  private static @Nullable Boolean thirdPartyDeposit(Payment facts) {
    return switch (facts.remitter()) {
      case HOLDER -> Boolean.FALSE;
      case GUARDIAN, ANOTHER_PERSON, ANOTHER_COMPANY -> Boolean.TRUE;
      case NO_CODE, COMPANY_NAME_WITHOUT_CODE, NOT_CHECKED ->
          facts.name() == MATCHES ? Boolean.FALSE : null;
    };
  }

  private void identityCheckFailure(SavingFundPayment payment, String reason) {
    returnAsUnattributed(payment, reason);
    findPayer(payment)
        .ifPresent(payer -> publishFailed(payer, new PartyId(PERSON, payer.getPersonalCode())));
  }

  private void identityCheckFailure(
      SavingFundPayment payment, PartyId namedAccount, String reason) {
    returnAsUnattributed(payment, reason);
    findPayer(payment).ifPresent(payer -> publishFailed(payer, namedAccount));
  }

  private void returnAsUnattributed(SavingFundPayment payment, String reason) {
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
  }

  private void publishFailed(User payer, PartyId recipient) {
    applicationEventPublisher.publishEvent(
        new SavingsPaymentFailedEvent(this, payer, Locale.of("et"), recipient));
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
