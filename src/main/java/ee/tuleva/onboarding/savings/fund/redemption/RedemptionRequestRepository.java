package ee.tuleva.onboarding.savings.fund.redemption;

import ee.tuleva.onboarding.savings.fund.redemption.RedemptionRequest.Status;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

public interface RedemptionRequestRepository extends CrudRepository<RedemptionRequest, UUID> {

  List<RedemptionRequest> findByStatus(Status status);

  List<RedemptionRequest> findByUserIdOrderByRequestedAtDesc(Long userId);

  List<RedemptionRequest> findByUserIdAndStatus(Long userId, Status status);

  List<RedemptionRequest> findByUserIdAndStatusIn(Long userId, List<Status> status);

  List<RedemptionRequest> findByStatusAndRequestedAtBeforeAndCancelledAtIsNull(
      Status status, Instant cutoff);

  List<RedemptionRequest> findByStatusAndRequestedAtBefore(Status status, Instant cutoff);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT r FROM RedemptionRequest r WHERE r.id = :id")
  Optional<RedemptionRequest> findByIdForUpdate(UUID id);

  @Modifying
  @Transactional
  @Query(
      """
    UPDATE RedemptionRequest r
          SET r.requestedAt = r.requestedAt - 1 DAY
          WHERE r.status = 'VERIFIED'
      AND r.requestedAt > CURRENT_TIMESTAMP - 1 DAY
      AND r.userId = :userId
    """)
  int TEST_backdateVerifiedRequests(@Param("userId") Long userId);
}
