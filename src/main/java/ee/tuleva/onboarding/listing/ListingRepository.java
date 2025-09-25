package ee.tuleva.onboarding.listing;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ListingRepository extends JpaRepository<Listing, Long> {
  @NotNull
  List<Listing> findByExpiryTimeAfter(Instant expiryTime);

  List<Listing> findByExpiryTimeAfterAndMemberIdEquals(Instant expiryTime, Long memberId);

  Long countListingsByExpiryTimeAfterAndStateEquals(Instant expiryTime, Listing.State state);

  Optional<Listing> findByIdAndMemberId(@NotNull Long id, @NotNull Long memberId);
}
