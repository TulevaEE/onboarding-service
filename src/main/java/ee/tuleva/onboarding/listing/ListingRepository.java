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

  Optional<Listing> findByIdAndMemberId(@NotNull Long id, @NotNull Long memberId);
}
