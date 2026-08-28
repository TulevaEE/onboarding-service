package ee.tuleva.onboarding.aml;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AmlCheckRepository extends JpaRepository<AmlCheck, Long> {

  boolean existsByPersonalCodeAndTypeAndCreatedTimeAfter(
      String personalCode, AmlCheckType type, Instant createdAfter);

  List<AmlCheck> findAllByPersonalCodeAndCreatedTimeAfter(
      String personalCode, Instant createdAfter);

  List<AmlCheck> findAllByPersonalCodeAndCompanyIdAndCreatedTimeAfter(
      String personalCode, @Nullable UUID companyId, Instant createdAfter);

  List<AmlCheck> findAllByTypeIn(List<AmlCheckType> types);

  List<AmlCheck> findAllByPersonalCodeAndTypeAndSuccess(
      String personalCode, AmlCheckType type, boolean success);

  List<AmlCheck> findAllByPersonalCodeAndTypeAndSuccessIsFalseAndCreatedTimeAfter(
      String personalCode, AmlCheckType type, Instant createdTimeAfter);

  Optional<AmlCheck> findFirstByPersonalCodeAndTypeAndCreatedTimeAfterOrderByCreatedTimeDescIdDesc(
      String personalCode, AmlCheckType type, Instant createdTime);

  Optional<AmlCheck> findFirstByPersonalCodeAndTypeOrderByCreatedTimeDescIdDesc(
      String personalCode, AmlCheckType type);

  Optional<AmlCheck> findFirstByCompanyIdAndTypeOrderByCreatedTimeDescIdDesc(
      UUID companyId, AmlCheckType type);

  Optional<AmlCheck> findFirstByPersonalCodeAndTypeInOrderByCreatedTimeDescIdDesc(
      String personalCode, Collection<AmlCheckType> types);

  List<AmlCheck> findAllByPersonalCodeAndType(String personalCode, AmlCheckType type);
}
