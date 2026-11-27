package ee.tuleva.onboarding.user;

import static java.util.function.Predicate.not;

import ee.tuleva.onboarding.user.contact.ContactChangeRepository;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class ReplacedContacts {

  private final ContactChangeRepository contactChangeRepository;

  @Transactional(readOnly = true)
  public boolean anyBetween(Long userId, Instant from, Instant until) {
    return contactChangeRepository.replacementsBetween(userId, from, until).stream()
        .anyMatch(not(ContactChange::onlyChangesLetterCase));
  }
}
