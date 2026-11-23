package ee.tuleva.onboarding.user;

import static java.util.function.Function.identity;
import static java.util.function.Predicate.not;
import static java.util.stream.Collectors.toMap;

import ee.tuleva.onboarding.user.contact.ContactChangeRepository;
import ee.tuleva.onboarding.user.contact.ContactChangeRepository.ReplacedEmail;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class RecentlyReplacedEmails {

  private final ContactChangeRepository contactChangeRepository;
  private final ContactChangeProperties properties;

  @Transactional(readOnly = true)
  public List<String> of(Long userId, Instant asOf) {
    Instant from = asOf.minus(properties.replacedEmailNoticeWindow());
    return List.copyOf(
        contactChangeRepository.emailsReplacedBetween(userId, from, asOf).stream()
            .filter(not(ReplacedEmail::isTheCurrentAddress))
            .map(ReplacedEmail::address)
            .collect(
                toMap(
                    AsciiLetterCase::fold,
                    identity(),
                    (newest, older) -> newest,
                    LinkedHashMap::new))
            .values());
  }
}
