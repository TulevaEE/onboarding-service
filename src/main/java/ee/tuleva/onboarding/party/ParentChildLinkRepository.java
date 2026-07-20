package ee.tuleva.onboarding.party;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ParentChildLinkRepository extends JpaRepository<ParentChildLink, UUID> {

  // THE INVARIANT: the three "active representation" / "other active parents" queries below
  // additionally require status = ACTIVE, so a PENDING_KYC link authorizes nothing. Every access
  // path inherits it because it reads through these methods: RoleSwitchService (via
  // ParentChildLinkService.isActiveRepresentation / findActivelyRepresentedChildCodes),
  // PaymentVerificationService.isActiveRepresentation, and
  // ParentChildLinkNotificationSender.otherActiveParents (which reads this repository directly).
  // The status filter is baked into the @Query so it cannot be forgotten at a call site.
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

  // Used by find-or-create AND by activation; deliberately NOT status-filtered so activation can
  // load a PENDING_KYC link and flip it.
  Optional<ParentChildLink> findByParentPersonalCodeAndChildPersonalCodeAndRelationshipType(
      String parentPersonalCode, String childPersonalCode, RepresentationType relationshipType);

  // A parent's still-valid PENDING_KYC links, for the "join the account" account-selector entries.
  List<ParentChildLink> findByParentPersonalCodeAndStatusAndSuspendedAtIsNullAndValidUntilAfter(
      String parentPersonalCode, ParentChildLinkStatus status, LocalDate date);
}
