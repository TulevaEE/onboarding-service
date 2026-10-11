package ee.tuleva.onboarding.instrument;

import static org.springframework.transaction.annotation.Propagation.REQUIRES_NEW;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

public interface InstrumentReferenceRepository extends JpaRepository<InstrumentReference, Long> {

  Optional<InstrumentReference> findByIsin(String isin);

  List<InstrumentReference> findAllByOrderByIdAsc();

  @Modifying(clearAutomatically = true)
  @Transactional(propagation = REQUIRES_NEW)
  @Query(
      "UPDATE InstrumentReference i SET i.active = false WHERE i.isin = :isin AND i.active = true")
  int deactivateInItsOwnTransaction(String isin);
}
