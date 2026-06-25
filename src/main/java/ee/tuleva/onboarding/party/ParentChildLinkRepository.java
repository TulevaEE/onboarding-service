package ee.tuleva.onboarding.party;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ParentChildLinkRepository extends JpaRepository<ParentChildLink, UUID> {

  List<ParentChildLink> findByParentPersonalCodeAndSuspendedAtIsNullAndValidUntilAfter(
      String parentPersonalCode, LocalDate date);

  boolean existsByParentPersonalCodeAndChildPersonalCodeAndSuspendedAtIsNullAndValidUntilAfter(
      String parentPersonalCode, String childPersonalCode, LocalDate date);

  Optional<ParentChildLink> findByParentPersonalCodeAndChildPersonalCodeAndRelationshipType(
      String parentPersonalCode, String childPersonalCode, RepresentationType relationshipType);
}
