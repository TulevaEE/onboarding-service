package ee.tuleva.onboarding.instrument;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface InstrumentReferenceRepository extends JpaRepository<InstrumentReference, Long> {

  Optional<InstrumentReference> findByIsin(String isin);

  List<InstrumentReference> findAllByOrderByIdAsc();

  @Modifying(clearAutomatically = true)
  @Query(
      "UPDATE InstrumentReference i SET i.active = false WHERE i.isin = :isin AND i.active = true")
  int deactivate(String isin);
}
