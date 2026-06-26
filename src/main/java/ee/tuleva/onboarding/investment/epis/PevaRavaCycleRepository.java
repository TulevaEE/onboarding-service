package ee.tuleva.onboarding.investment.epis;

import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PevaRavaCycleRepository extends JpaRepository<PevaRavaCycleEntity, Long> {

  Optional<PevaRavaCycleEntity> findByExecDate(LocalDate execDate);

  Optional<PevaRavaCycleEntity> findTopByExecDateGreaterThanEqualOrderByExecDateAsc(LocalDate date);
}
