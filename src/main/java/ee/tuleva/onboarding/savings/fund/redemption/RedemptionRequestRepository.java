package ee.tuleva.onboarding.savings.fund.redemption;

import ee.tuleva.onboarding.party.PartyId;
import ee.tuleva.onboarding.savings.fund.redemption.RedemptionRequest.Status;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
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

  List<RedemptionRequest> findByStatusIn(Collection<Status> statuses);

  Optional<RedemptionRequest> findByIdAndStatus(UUID id, Status status);

  List<RedemptionRequest> findByPartyTypeAndPartyCodeAndStatusIn(
      PartyId.Type partyType, String partyCode, List<Status> statuses);

  List<RedemptionRequest> findByStatusAndRequestedAtBeforeAndCancelledAtIsNull(
      Status status, Instant cutoff);

  @Query(
      """
      SELECT r FROM RedemptionRequest r
      WHERE r.status = :status
        AND COALESCE(r.requeuedAt, r.requestedAt) < :cutoff
      """)
  List<RedemptionRequest> findAcceptedBefore(
      @Param("status") Status status, @Param("cutoff") Instant cutoff);

  @Query(
      """
      SELECT r FROM RedemptionRequest r
      WHERE r.holdReasons IS NOT EMPTY
        AND r.holdReleasedAt IS NULL
        AND r.holdNotifiedAt IS NULL
        AND r.status IN :statuses
      """)
  List<RedemptionRequest> findWithUnsentHoldNotification(
      @Param("statuses") Collection<Status> statuses);

  @Modifying
  @Transactional
  @Query(
      """
      UPDATE RedemptionRequest r
         SET r.holdNotifiedAt = :notifiedAt
       WHERE r.id = :id
         AND r.holdNotifiedAt IS NULL
      """)
  int markHoldNotified(@Param("id") UUID id, @Param("notifiedAt") Instant notifiedAt);

  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Transactional
  @Query(
      """
      UPDATE RedemptionRequest r
         SET r.verificationAttemptedAt = :attemptedAt
       WHERE r.id = :id
      """)
  int markVerificationAttempted(@Param("id") UUID id, @Param("attemptedAt") Instant attemptedAt);

  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Transactional
  @Query("UPDATE RedemptionRequest r SET r.errorReason = :errorReason WHERE r.id = :id")
  int markErrorReason(@Param("id") UUID id, @Param("errorReason") String errorReason);

  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Transactional
  @Query("UPDATE RedemptionRequest r SET r.batchId = :batchId WHERE r.id IN :ids")
  int assignBatch(@Param("ids") Collection<UUID> ids, @Param("batchId") UUID batchId);

  @Query("SELECT r.id FROM RedemptionRequest r WHERE r.batchId = :batchId")
  List<UUID> findIdsByBatchId(@Param("batchId") UUID batchId);

  @Query(
      """
      SELECT COALESCE(SUM(r.cashAmount), 0)
        FROM RedemptionRequest r
       WHERE r.batchId = :batchId
         AND r.status = :status
      """)
  BigDecimal sumCashAmount(@Param("batchId") UUID batchId, @Param("status") Status status);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT r FROM RedemptionRequest r WHERE r.id = :id")
  Optional<RedemptionRequest> findByIdForUpdate(UUID id);

  @Modifying
  @Transactional
  @Query(
      """
    UPDATE RedemptionRequest r
          SET r.requestedAt = r.requestedAt - 2 DAY
          WHERE r.status = 'VERIFIED'
      AND r.requestedAt > CURRENT_TIMESTAMP - 2 DAY
      AND r.userId = :userId
    """)
  int TEST_backdateVerifiedRequests(@Param("userId") Long userId);
}
