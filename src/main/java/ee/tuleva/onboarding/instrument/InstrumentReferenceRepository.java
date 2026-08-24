package ee.tuleva.onboarding.instrument;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InstrumentReferenceRepository extends JpaRepository<InstrumentReference, Long> {

  Optional<InstrumentReference> findByIsin(String isin);
}
