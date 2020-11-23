package ee.tuleva.onboarding.aml;

import ee.tuleva.onboarding.user.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface AmlCheckRepository extends JpaRepository<AmlCheck, Long> {

    boolean existsByUserAndTypeAndCreatedTimeAfter(User user, AmlCheckType type, Instant createdAfter);

    List<AmlCheck> findAllByUserAndCreatedTimeAfter(User user, Instant createdAfter);
}
