package ee.tuleva.onboarding.investment.instrument;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InstrumentReferenceRepository extends JpaRepository<InstrumentReference, Long> {

  Optional<InstrumentReference> findByIsin(String isin);

  List<InstrumentReference> findByIsinIn(List<String> isins);

  List<InstrumentReference> findByActiveTrue();
}
