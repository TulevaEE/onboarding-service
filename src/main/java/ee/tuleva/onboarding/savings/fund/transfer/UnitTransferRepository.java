package ee.tuleva.onboarding.savings.fund.transfer;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;

public interface UnitTransferRepository extends CrudRepository<UnitTransfer, UUID> {

  List<UnitTransfer> findAllByStateOrderByCreatedAtDesc(UnitTransferState state);

  Optional<UnitTransfer> findFirstByPlanHashAndStateOrderByCreatedAtAsc(
      String planHash, UnitTransferState state);

  boolean existsByPlanHashAndState(String planHash, UnitTransferState state);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT t FROM UnitTransfer t WHERE t.id = :id")
  Optional<UnitTransfer> findByIdForUpdate(UUID id);
}
