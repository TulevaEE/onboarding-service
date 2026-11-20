package ee.tuleva.onboarding.investment.transaction;

import static jakarta.persistence.LockModeType.PESSIMISTIC_FORCE_INCREMENT;
import static jakarta.persistence.LockModeType.PESSIMISTIC_READ;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NullMarked;

@RequiredArgsConstructor
@NullMarked
class TransactionBatchLockingImpl implements TransactionBatchLocking {

  @PersistenceContext private final EntityManager entityManager;

  @Override
  public void lockAgainstConcurrentConfirmation(TransactionBatch batch) {
    entityManager.lock(batch, PESSIMISTIC_FORCE_INCREMENT);
  }

  @Override
  public void lockAgainstConcurrentChange(TransactionBatch batch) {
    entityManager.lock(batch, PESSIMISTIC_READ);
  }
}
