package ee.tuleva.onboarding.savings.fund;

import static ee.tuleva.onboarding.savings.fund.SavingFundPayment.Status.TO_BE_RETURNED;
import static ee.tuleva.onboarding.savings.fund.SavingFundPayment.Status.VERIFIED;

import ee.tuleva.onboarding.ledger.SavingsFundLedger;
import ee.tuleva.onboarding.payment.event.SavingsPaymentFailedEvent;
import ee.tuleva.onboarding.user.UserRepository;
import ee.tuleva.onboarding.user.personalcode.PersonalCodeValidator;
import java.text.Normalizer;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
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

  private final SavingFundPaymentRepository savingFundPaymentRepository;
  private final UserRepository userRepository;
  private final SavingsFundOnboardingService savingsFundOnboardingService;
  private final SavingsFundLedger savingsFundLedger;
  private final ApplicationEventPublisher applicationEventPublisher;

  @Transactional
  public void process(SavingFundPayment payment) {
    log.info("Processing payment {}", payment.getId());

    var remitterPersonalCodeProvided = extractPersonalCode(payment.getRemitterIdCode()).isPresent();

    var personalCodeFromDescription = extractPersonalCode(payment.getDescription());
    if (personalCodeFromDescription.isEmpty()) {
      identityCheckFailure(payment, "selgituses puudub isikukood");
      return;
    }
    if (remitterPersonalCodeProvided
        && !personalCodeFromDescription.get().equals(payment.getRemitterIdCode())) {
      identityCheckFailure(payment, "selgituses olev isikukood ei klapi maksja isikukoodiga");
      return;
    }

    var user = userRepository.findByPersonalCode(personalCodeFromDescription.get());
    if (user.isEmpty()) {
      identityCheckFailure(payment, "isik ei ole Tuleva klient");
      return;
    }
    if (!remitterPersonalCodeProvided
        && !isSameName(user.get().getFullName(), payment.getRemitterName())) {
      identityCheckFailure(payment, "maksja nimi ei klapi Tuleva andmetega");
      return;
    }
    if (!savingsFundOnboardingService.isOnboardingCompleted(user.get())) {
      identityCheckFailure(payment, "see isik ei ole täiendava kogumisfondiga liitunud");
      return;
    }

    var userId = user.get().getId();
    savingFundPaymentRepository.attachUser(payment.getId(), userId);

    log.info(
        "Verification completed for payment {}, attaching to user {}", payment.getId(), userId);
    savingFundPaymentRepository.changeStatus(payment.getId(), VERIFIED);
    savingsFundLedger.recordPaymentReceived(user.get(), payment.getAmount(), payment.getId());
  }

  private void identityCheckFailure(SavingFundPayment payment, String reason) {
    log.info("Identity check failed for payment {}: {}", payment.getId(), reason);
    savingFundPaymentRepository.changeStatus(payment.getId(), TO_BE_RETURNED);
    savingFundPaymentRepository.addReturnReason(payment.getId(), reason);

    savingsFundLedger.recordUnattributedPayment(payment.getAmount(), payment.getId());

    Optional.ofNullable(payment.getUserId())
        .flatMap(userRepository::findById)
        .ifPresent(
            user ->
                applicationEventPublisher.publishEvent(
                    new SavingsPaymentFailedEvent(user, Locale.of("et"))));
  }

  Optional<String> extractPersonalCode(String description) {
    if (description == null) return Optional.empty();
    var matcher = Pattern.compile("(?<![0-9])[0-9]{11}(?![0-9])").matcher(description);
    if (!matcher.find()) return Optional.empty();
    var possiblePersonalCode = matcher.group();
    if (personalCodeValidator.isValid(possiblePersonalCode))
      return Optional.of(possiblePersonalCode);
    return Optional.empty();
  }

  boolean isSameName(String name1, String name2) {
    if (name1 == null || name2 == null) return false;
    return normalize(name1.strip()).equals(normalize(name2.strip()));
  }

  private String normalize(String name) {
    return Arrays.stream(name.replaceAll("\\p{Punct}", " ").split("\\s+"))
        .map(String::strip)
        .map(String::toUpperCase)
        .map(s -> Normalizer.normalize(s, Normalizer.Form.NFKD).replaceAll("\\p{M}", ""))
        .sorted()
        .collect(Collectors.joining(" "));
  }
}
