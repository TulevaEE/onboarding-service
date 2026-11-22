package ee.tuleva.onboarding.user.contact;

import static ee.tuleva.onboarding.user.ContactChangeSource.SELF_SERVICE;
import static ee.tuleva.onboarding.user.ContactField.EMAIL;
import static ee.tuleva.onboarding.user.ContactField.PHONE;
import static java.time.temporal.ChronoUnit.MICROS;

import ee.tuleva.onboarding.user.ContactChange;
import ee.tuleva.onboarding.user.ContactDetailsChanged;
import ee.tuleva.onboarding.user.ContactField;
import ee.tuleva.onboarding.user.User;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ContactChangeRecorder {

  private final ContactChangeRepository repository;
  private final ApplicationEventPublisher eventPublisher;
  private final Clock clock;

  public void recordSelfServiceChange(
      User changedUser, @Nullable String oldEmail, @Nullable String oldPhoneNumber) {
    Instant changedAt = clock.instant().truncatedTo(MICROS);
    List<ContactChange> changes =
        Stream.of(
                change(EMAIL, oldEmail, changedUser.getEmail(), changedAt),
                change(PHONE, oldPhoneNumber, changedUser.getPhoneNumber(), changedAt))
            .flatMap(Optional::stream)
            .toList();
    if (changes.isEmpty()) {
      return;
    }
    repository.saveAll(changedUser.getIdOrThrow(), SELF_SERVICE, changes);
    eventPublisher.publishEvent(new ContactDetailsChanged(changedUser, SELF_SERVICE, changes));
  }

  private static Optional<ContactChange> change(
      ContactField field, @Nullable String oldValue, @Nullable String newValue, Instant changedAt) {
    String normalizedOldValue = blankToNull(oldValue);
    String normalizedNewValue = blankToNull(newValue);
    if (Objects.equals(normalizedOldValue, normalizedNewValue)) {
      return Optional.empty();
    }
    return Optional.of(new ContactChange(field, normalizedOldValue, normalizedNewValue, changedAt));
  }

  private static @Nullable String blankToNull(@Nullable String value) {
    return value == null || value.isBlank() ? null : value;
  }
}
