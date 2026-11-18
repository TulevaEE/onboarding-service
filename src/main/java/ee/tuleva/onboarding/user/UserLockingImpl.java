package ee.tuleva.onboarding.user;

import static jakarta.persistence.LockModeType.PESSIMISTIC_WRITE;
import static org.springframework.transaction.annotation.Propagation.MANDATORY;

import jakarta.persistence.EntityManager;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
class UserLockingImpl implements UserLocking {

  private final EntityManager entityManager;

  @Override
  @Transactional(propagation = MANDATORY)
  public Optional<User> lockForUpdate(String personalCode) {
    return entityManager
        .createQuery("select u from User u where u.personalCode = :personalCode", User.class)
        .setParameter("personalCode", personalCode)
        .setLockMode(PESSIMISTIC_WRITE)
        .getResultList()
        .stream()
        .findFirst()
        .map(this::currentState);
  }

  private User currentState(User lockedUser) {
    entityManager.refresh(lockedUser);
    return lockedUser;
  }
}
