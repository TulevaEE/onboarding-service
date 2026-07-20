package ee.tuleva.onboarding.party;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ParentChildLinkRepository extends JpaRepository<ParentChildLink, UUID> {

  // Security invariant: the "active representation" queries require status = ACTIVE, so a
  // PENDING_KYC link never authorizes anything. Do not remove the status filter from these
  // @Queries.
  @Query(
      "select l from ParentChildLink l"
          + " where l.parentPersonalCode = ?1"
          + " and l.suspendedAt is null"
          + " and l.validUntil > ?2"
          + " and l.status = ee.tuleva.onboarding.party.ParentChildLinkStatus.ACTIVE")
  List<ParentChildLink> findByParentPersonalCodeAndSuspendedAtIsNullAndValidUntilAfter(
      String parentPersonalCode, LocalDate date);

  @Query(
      "select l from ParentChildLink l"
          + " where l.childPersonalCode = ?1"
          + " and l.suspendedAt is null"
          + " and l.validUntil > ?2"
          + " and l.status = ee.tuleva.onboarding.party.ParentChildLinkStatus.ACTIVE")
  List<ParentChildLink> findByChildPersonalCodeAndSuspendedAtIsNullAndValidUntilAfter(
      String childPersonalCode, LocalDate date);

  @Query(
      "select count(l) > 0 from ParentChildLink l"
          + " where l.parentPersonalCode = ?1"
          + " and l.childPersonalCode = ?2"
          + " and l.suspendedAt is null"
          + " and l.validUntil > ?3"
          + " and l.status = ee.tuleva.onboarding.party.ParentChildLinkStatus.ACTIVE")
  boolean existsByParentPersonalCodeAndChildPersonalCodeAndSuspendedAtIsNullAndValidUntilAfter(
      String parentPersonalCode, String childPersonalCode, LocalDate date);

  Optional<ParentChildLink> findByParentPersonalCodeAndChildPersonalCodeAndRelationshipType(
      String parentPersonalCode, String childPersonalCode, RepresentationType relationshipType);

  List<ParentChildLink> findByParentPersonalCodeAndStatusAndSuspendedAtIsNullAndValidUntilAfter(
      String parentPersonalCode, ParentChildLinkStatus status, LocalDate date);
}
