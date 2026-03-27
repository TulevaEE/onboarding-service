package ee.tuleva.onboarding.savings.fund.redemption;

import ee.tuleva.onboarding.party.PartyId;
import ee.tuleva.onboarding.savings.fund.redemption.RedemptionRequest.Status;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;

public interface RedemptionRequestRepository extends CrudRepository<RedemptionRequest, UUID> {

  List<RedemptionRequest> findByStatus(Status status);

  List<RedemptionRequest> findByPartyTypeAndPartyCodeOrderByRequestedAtDesc(
      PartyId.Type partyType, String partyCode);

  List<RedemptionRequest> findByPartyTypeAndPartyCodeAndStatus(
      PartyId.Type partyType, String partyCode, Status status);

  Optional<RedemptionRequest> findByIdAndStatus(UUID id, Status status);

  List<RedemptionRequest> findByPartyTypeAndPartyCodeAndStatusIn(
      PartyId.Type partyType, String partyCode, List<Status> status);

  List<RedemptionRequest> findByStatusAndRequestedAtBeforeAndCancelledAtIsNull(
      Status status, Instant cutoff);

  List<RedemptionRequest> findByStatusAndRequestedAtBefore(Status status, Instant cutoff);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT r FROM RedemptionRequest r WHERE r.id = :id")
  Optional<RedemptionRequest> findByIdForUpdate(UUID id);
}
