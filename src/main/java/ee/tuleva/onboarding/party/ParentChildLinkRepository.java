package ee.tuleva.onboarding.party;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ParentChildLinkRepository extends JpaRepository<ParentChildLink, UUID> {

  List<ParentChildLink> findByParentPersonalCodeAndStatusAndSuspendedAtIsNullAndValidUntilAfter(
      String parentPersonalCode, ParentChildLinkStatus status, LocalDate date);

  List<ParentChildLink> findByChildPersonalCodeAndStatusAndSuspendedAtIsNullAndValidUntilAfter(
      String childPersonalCode, ParentChildLinkStatus status, LocalDate date);

  boolean
      existsByParentPersonalCodeAndChildPersonalCodeAndStatusAndSuspendedAtIsNullAndValidUntilAfter(
          String parentPersonalCode,
          String childPersonalCode,
          ParentChildLinkStatus status,
          LocalDate date);

  Optional<ParentChildLink> findByParentPersonalCodeAndChildPersonalCodeAndRelationshipType(
      String parentPersonalCode, String childPersonalCode, RepresentationType relationshipType);
}
