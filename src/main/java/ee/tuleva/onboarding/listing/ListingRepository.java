package ee.tuleva.onboarding.listing;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ListingRepository extends JpaRepository<Listing, Long> {
  List<Listing> findByExpiryTimeAfter(Instant time);
}
