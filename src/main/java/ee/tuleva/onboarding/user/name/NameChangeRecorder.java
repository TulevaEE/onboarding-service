package ee.tuleva.onboarding.user.name;

import ee.tuleva.onboarding.user.NameChangeBasis;
import ee.tuleva.onboarding.user.User;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class NameChangeRecorder {

  private final NameChangeRepository repository;
  private final Clock clock;

  public void record(
      User changedUser,
      @Nullable String oldFirstName,
      @Nullable String oldLastName,
      NameChangeBasis basis) {
    repository.save(
        changedUser.getIdOrThrow(),
        new NameChange(
            oldFirstName,
            oldLastName,
            changedUser.getFirstName(),
            changedUser.getLastName(),
            basis,
            clock.instant()));
  }

  public void recordFirst(User createdUser, NameChangeBasis basis) {
    record(createdUser, null, null, basis);
  }
}
