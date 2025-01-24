package ee.tuleva.onboarding.aml;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AmlCheckRepository extends JpaRepository<AmlCheck, Long> {

  boolean existsByPersonalCodeAndTypeAndCreatedTimeAfter(
      String personalCode, AmlCheckType type, Instant createdAfter);

  List<AmlCheck> findAllByPersonalCodeAndCreatedTimeAfter(
      String personalCode, Instant createdAfter);

  List<AmlCheck> findAllByTypeIn(List<AmlCheckType> types);

  List<AmlCheck> findAllByPersonalCodeAndTypeAndSuccess(
      String personalCode, AmlCheckType type, boolean success);

  List<AmlCheck> findAllByPersonalCodeAndTypeAndSuccessIsFalseAndCreatedTimeAfter(
      String personalCode, AmlCheckType type, Instant createdTimeAfter);
}
