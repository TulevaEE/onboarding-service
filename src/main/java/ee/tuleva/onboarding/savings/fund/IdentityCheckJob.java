package ee.tuleva.onboarding.savings.fund;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ee.tuleva.onboarding.user.UserRepository;
import ee.tuleva.onboarding.user.personalcode.PersonalCodeValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class IdentityCheckJob {
  private final static PersonalCodeValidator personalCodeValidator = new PersonalCodeValidator();

  private final IdentityCheckRepository identityCheckRepository;
  private final SavingFundPaymentRepository savingFundPaymentRepository;
  private final SavingsFundOnboardingService savingsFundOnboardingService;
  private final UserRepository userRepository;

  @Scheduled(fixedRateString = "1m")
  public void runJob() {
    identityCheckRepository.findPaymentsWithoutIdentityCheck().forEach(id -> {
      try {
        savingFundPaymentRepository.findById(id).ifPresent(this::process);
      } catch (Exception e) {
        log.error("Identity check failed for payment {}", id, e);
      }
    });
  }

  @Transactional
  public void process(SavingFundPayment payment) {
    var remitterPersonalCodeProvided = payment.getRemitterIdCode() != null;

    var personalCodeFromDescription = extractPersonalCode(payment.getDescription());
    if (personalCodeFromDescription.isEmpty()) {
      identityCheckRepository.identityCheckFailure(payment, "selgituses puudub isikukood");
      return;
    }
    if (remitterPersonalCodeProvided && !personalCodeFromDescription.get().equals(payment.getRemitterIdCode())) {
      identityCheckRepository.identityCheckFailure(payment, "selgituses olev isikukood ei klapi maksja isikukoodiga");
      return;
    }

    var user = userRepository.findByPersonalCode(personalCodeFromDescription.get());
    if (user.isEmpty()) {
      identityCheckRepository.identityCheckFailure(payment, "isik ei ole Tuleva klient");
      return;
    }
    if (!remitterPersonalCodeProvided && !isSameName(user.get().getFullName(), payment.getRemitterName())) {
      identityCheckRepository.identityCheckFailure(payment, "maksja nimi ei klapi Tuleva andmetega");
      return;
    }
    if (!savingsFundOnboardingService.isOnboardingCompleted(user.get())) {
      identityCheckRepository.identityCheckFailure(payment, "see isik ei ole täiendava kogumisfondiga liitunud");
      return;
    }

    identityCheckRepository.identityCheckSuccess(payment, user.get());

    // todo in case of identity check failure -> ensure that userId is detached from payment in case it was added there by Montonio
  }

  Optional<String> extractPersonalCode(String description) {
    if (description == null) return Optional.empty();
    var matcher = Pattern.compile("(?<![0-9])[0-9]{11}(?![0-9])").matcher(description);
    if (!matcher.find()) return Optional.empty();
    var possiblePersonalCode = matcher.group();
    if (personalCodeValidator.isValid(possiblePersonalCode)) return Optional.of(possiblePersonalCode);
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
