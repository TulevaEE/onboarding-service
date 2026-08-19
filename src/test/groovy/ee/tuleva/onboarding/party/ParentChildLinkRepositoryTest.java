package ee.tuleva.onboarding.party;

import static ee.tuleva.onboarding.party.ParentChildLinkStatus.ACTIVE;
import static ee.tuleva.onboarding.party.ParentChildLinkStatus.PENDING_KYC;
import static ee.tuleva.onboarding.party.RepresentationType.GUARDIAN;
import static ee.tuleva.onboarding.party.RepresentationType.LEGAL_REPRESENTATIVE;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

@DataJpaTest
class ParentChildLinkRepositoryTest {

  @Autowired ParentChildLinkRepository repository;

  private static final String PARENT = "38001010000";
  private static final String OTHER_PARENT = "38002020008";
  private static final String CHILD = "61001010000";
  private static final LocalDate EIGHTEENTH_BIRTHDAY = LocalDate.of(2030, 1, 1);
  private static final Instant SUSPENDED = Instant.parse("2026-05-22T00:00:00Z");

  private ParentChildLink savedLink(Instant suspendedAt) {
    return savedLink(PARENT, suspendedAt);
  }

  private ParentChildLink savedLink(String parentPersonalCode, Instant suspendedAt) {
    return repository.save(
        ParentChildLink.builder()
            .parentPersonalCode(parentPersonalCode)
            .childPersonalCode(CHILD)
            .relationshipType(LEGAL_REPRESENTATIVE)
            .validUntil(EIGHTEENTH_BIRTHDAY)
            .suspendedAt(suspendedAt)
            .build());
  }

  private ParentChildLink savedPendingLink(String parentPersonalCode) {
    return savedPendingLink(parentPersonalCode, null);
  }

  private ParentChildLink savedPendingLink(String parentPersonalCode, Instant suspendedAt) {
    return repository.save(
        ParentChildLink.builder()
            .parentPersonalCode(parentPersonalCode)
            .childPersonalCode(CHILD)
            .relationshipType(LEGAL_REPRESENTATIVE)
            .validUntil(EIGHTEENTH_BIRTHDAY)
            .status(PENDING_KYC)
            .suspendedAt(suspendedAt)
            .build());
  }

  private ParentChildLink savedGuardianLink(
      String parentPersonalCode, ParentChildLinkStatus status) {
    return repository.save(
        ParentChildLink.builder()
            .parentPersonalCode(parentPersonalCode)
            .childPersonalCode(CHILD)
            .relationshipType(GUARDIAN)
            .validUntil(EIGHTEENTH_BIRTHDAY)
            .status(status)
            .build());
  }

  @Test
  void builderDefaultsStatusToActive() {
    var link = savedLink(null);

    assertThat(link.getStatus()).isEqualTo(ACTIVE);
    assertThat(link.isPending()).isFalse();
  }

  @Test
  void activeRepresentationQueriesExcludePendingLinks() {
    savedPendingLink(PARENT);

    // the role switch reads through these two queries
    assertThat(
            repository.findByParentPersonalCodeAndStatusAndSuspendedAtIsNullAndValidUntilAfter(
                PARENT, ACTIVE, EIGHTEENTH_BIRTHDAY.minusDays(1)))
        .isEmpty();
    assertThat(
            repository
                .existsByParentPersonalCodeAndChildPersonalCodeAndStatusInAndSuspendedAtIsNullAndValidUntilAfter(
                    PARENT, CHILD, Set.of(ACTIVE), EIGHTEENTH_BIRTHDAY.minusDays(1)))
        .isFalse();
  }

  @Test
  void otherActiveParentsQueryExcludesPendingLinks() {
    // the notification sender reads this query directly, so it must not see pending co-parents
    savedPendingLink(PARENT);

    assertThat(
            repository.findByChildPersonalCodeAndStatusAndSuspendedAtIsNullAndValidUntilAfter(
                CHILD, ACTIVE, EIGHTEENTH_BIRTHDAY.minusDays(1)))
        .isEmpty();
  }

  @Test
  void findsOnlyPendingLinksForParent() {
    var pending = savedPendingLink(PARENT);
    savedLink(OTHER_PARENT, null); // an ACTIVE link must not show up

    assertThat(
            repository.findByParentPersonalCodeAndStatusAndSuspendedAtIsNullAndValidUntilAfter(
                PARENT, PENDING_KYC, EIGHTEENTH_BIRTHDAY.minusDays(1)))
        .containsExactly(pending);
  }

  @Test
  void findsActiveLinkBeforeValidUntil() {
    var link = savedLink(null);

    assertThat(
            repository.findByParentPersonalCodeAndStatusAndSuspendedAtIsNullAndValidUntilAfter(
                PARENT, ACTIVE, EIGHTEENTH_BIRTHDAY.minusDays(1)))
        .containsExactly(link);
  }

  @Test
  void doesNotFindLinkOnValidUntilDate() {
    savedLink(null);

    assertThat(
            repository.findByParentPersonalCodeAndStatusAndSuspendedAtIsNullAndValidUntilAfter(
                PARENT, ACTIVE, EIGHTEENTH_BIRTHDAY))
        .isEmpty();
  }

  @Test
  void doesNotFindLinkAfterValidUntil() {
    savedLink(null);

    assertThat(
            repository.findByParentPersonalCodeAndStatusAndSuspendedAtIsNullAndValidUntilAfter(
                PARENT, ACTIVE, EIGHTEENTH_BIRTHDAY.plusDays(1)))
        .isEmpty();
  }

  @Test
  void doesNotFindSuspendedLink() {
    savedLink(SUSPENDED);

    assertThat(
            repository.findByParentPersonalCodeAndStatusAndSuspendedAtIsNullAndValidUntilAfter(
                PARENT, ACTIVE, EIGHTEENTH_BIRTHDAY.minusDays(1)))
        .isEmpty();
  }

  @Test
  void representsActiveChildBeforeValidUntil() {
    savedLink(null);

    assertThat(
            repository
                .existsByParentPersonalCodeAndChildPersonalCodeAndStatusInAndSuspendedAtIsNullAndValidUntilAfter(
                    PARENT, CHILD, Set.of(ACTIVE), EIGHTEENTH_BIRTHDAY.minusDays(1)))
        .isTrue();
  }

  @Test
  void representsActiveChildWhenTheStatusSetIsWidened() {
    savedLink(null);

    assertThat(
            repository
                .existsByParentPersonalCodeAndChildPersonalCodeAndStatusInAndSuspendedAtIsNullAndValidUntilAfter(
                    PARENT, CHILD, Set.of(ACTIVE, PENDING_KYC), EIGHTEENTH_BIRTHDAY.minusDays(1)))
        .isTrue();
  }

  @Test
  void representsPendingChildOnlyWhenTheStatusSetIncludesPendingKyc() {
    // payments ask for the widened set, so a parent who has not cleared KYC still counts;
    // the role switch asks for ACTIVE only and must not see them
    savedPendingLink(PARENT);

    assertThat(
            repository
                .existsByParentPersonalCodeAndChildPersonalCodeAndStatusInAndSuspendedAtIsNullAndValidUntilAfter(
                    PARENT, CHILD, Set.of(ACTIVE, PENDING_KYC), EIGHTEENTH_BIRTHDAY.minusDays(1)))
        .isTrue();
    assertThat(
            repository
                .existsByParentPersonalCodeAndChildPersonalCodeAndStatusInAndSuspendedAtIsNullAndValidUntilAfter(
                    PARENT, CHILD, Set.of(ACTIVE), EIGHTEENTH_BIRTHDAY.minusDays(1)))
        .isFalse();
  }

  // guardians are deliberately not filtered out by relationship type: a court-appointed guardian
  // represents their ward exactly as a parent represents a child
  @Test
  void representsGuardianWardsOnTheSameTermsAsLegalRepresentatives() {
    var guardianLink = savedGuardianLink(PARENT, ACTIVE);
    savedGuardianLink(OTHER_PARENT, PENDING_KYC);

    assertThat(
            repository
                .existsByParentPersonalCodeAndChildPersonalCodeAndStatusInAndSuspendedAtIsNullAndValidUntilAfter(
                    PARENT, CHILD, Set.of(ACTIVE), EIGHTEENTH_BIRTHDAY.minusDays(1)))
        .isTrue();
    assertThat(
            repository
                .existsByParentPersonalCodeAndChildPersonalCodeAndStatusInAndSuspendedAtIsNullAndValidUntilAfter(
                    PARENT, CHILD, Set.of(ACTIVE, PENDING_KYC), EIGHTEENTH_BIRTHDAY.minusDays(1)))
        .isTrue();
    assertThat(
            repository
                .existsByParentPersonalCodeAndChildPersonalCodeAndStatusInAndSuspendedAtIsNullAndValidUntilAfter(
                    OTHER_PARENT,
                    CHILD,
                    Set.of(ACTIVE, PENDING_KYC),
                    EIGHTEENTH_BIRTHDAY.minusDays(1)))
        .isTrue();
    assertThat(
            repository
                .existsByParentPersonalCodeAndChildPersonalCodeAndStatusInAndSuspendedAtIsNullAndValidUntilAfter(
                    OTHER_PARENT, CHILD, Set.of(ACTIVE), EIGHTEENTH_BIRTHDAY.minusDays(1)))
        .isFalse();
    // the role listing reads through this query, which also ignores relationship type
    assertThat(
            repository.findByParentPersonalCodeAndStatusAndSuspendedAtIsNullAndValidUntilAfter(
                PARENT, ACTIVE, EIGHTEENTH_BIRTHDAY.minusDays(1)))
        .containsExactly(guardianLink);
    assertThat(
            repository.findByParentPersonalCodeAndStatusAndSuspendedAtIsNullAndValidUntilAfter(
                OTHER_PARENT, ACTIVE, EIGHTEENTH_BIRTHDAY.minusDays(1)))
        .isEmpty();
  }

  @Test
  void representsNoSuspendedActiveChildForAnyStatusSet() {
    savedLink(SUSPENDED);

    assertThat(
            repository
                .existsByParentPersonalCodeAndChildPersonalCodeAndStatusInAndSuspendedAtIsNullAndValidUntilAfter(
                    PARENT, CHILD, Set.of(ACTIVE), EIGHTEENTH_BIRTHDAY.minusDays(1)))
        .isFalse();
    assertThat(
            repository
                .existsByParentPersonalCodeAndChildPersonalCodeAndStatusInAndSuspendedAtIsNullAndValidUntilAfter(
                    PARENT, CHILD, Set.of(ACTIVE, PENDING_KYC), EIGHTEENTH_BIRTHDAY.minusDays(1)))
        .isFalse();
  }

  @Test
  void representsNoSuspendedPendingChildForAnyStatusSet() {
    savedPendingLink(PARENT, SUSPENDED);

    assertThat(
            repository
                .existsByParentPersonalCodeAndChildPersonalCodeAndStatusInAndSuspendedAtIsNullAndValidUntilAfter(
                    PARENT, CHILD, Set.of(ACTIVE), EIGHTEENTH_BIRTHDAY.minusDays(1)))
        .isFalse();
    assertThat(
            repository
                .existsByParentPersonalCodeAndChildPersonalCodeAndStatusInAndSuspendedAtIsNullAndValidUntilAfter(
                    PARENT, CHILD, Set.of(ACTIVE, PENDING_KYC), EIGHTEENTH_BIRTHDAY.minusDays(1)))
        .isFalse();
  }

  @Test
  void representsNoExpiredActiveChildForAnyStatusSet() {
    savedLink(null);

    assertThat(
            repository
                .existsByParentPersonalCodeAndChildPersonalCodeAndStatusInAndSuspendedAtIsNullAndValidUntilAfter(
                    PARENT, CHILD, Set.of(ACTIVE), EIGHTEENTH_BIRTHDAY))
        .isFalse();
    assertThat(
            repository
                .existsByParentPersonalCodeAndChildPersonalCodeAndStatusInAndSuspendedAtIsNullAndValidUntilAfter(
                    PARENT, CHILD, Set.of(ACTIVE, PENDING_KYC), EIGHTEENTH_BIRTHDAY))
        .isFalse();
  }

  @Test
  void representsNoExpiredPendingChildForAnyStatusSet() {
    savedPendingLink(PARENT);

    assertThat(
            repository
                .existsByParentPersonalCodeAndChildPersonalCodeAndStatusInAndSuspendedAtIsNullAndValidUntilAfter(
                    PARENT, CHILD, Set.of(ACTIVE), EIGHTEENTH_BIRTHDAY))
        .isFalse();
    assertThat(
            repository
                .existsByParentPersonalCodeAndChildPersonalCodeAndStatusInAndSuspendedAtIsNullAndValidUntilAfter(
                    PARENT, CHILD, Set.of(ACTIVE, PENDING_KYC), EIGHTEENTH_BIRTHDAY))
        .isFalse();
  }

  @Test
  void representsNoUnknownChildForAnyStatusSet() {
    savedLink(null);

    assertThat(
            repository
                .existsByParentPersonalCodeAndChildPersonalCodeAndStatusInAndSuspendedAtIsNullAndValidUntilAfter(
                    PARENT, "99999999999", Set.of(ACTIVE), EIGHTEENTH_BIRTHDAY.minusDays(1)))
        .isFalse();
    assertThat(
            repository
                .existsByParentPersonalCodeAndChildPersonalCodeAndStatusInAndSuspendedAtIsNullAndValidUntilAfter(
                    PARENT,
                    "99999999999",
                    Set.of(ACTIVE, PENDING_KYC),
                    EIGHTEENTH_BIRTHDAY.minusDays(1)))
        .isFalse();
  }

  @Test
  void findsAllActiveParentsOfChild() {
    var link = savedLink(PARENT, null);
    var otherLink = savedLink(OTHER_PARENT, null);

    assertThat(
            repository.findByChildPersonalCodeAndStatusAndSuspendedAtIsNullAndValidUntilAfter(
                CHILD, ACTIVE, EIGHTEENTH_BIRTHDAY.minusDays(1)))
        .containsExactlyInAnyOrder(link, otherLink);
  }

  @Test
  void doesNotFindSuspendedParentOfChild() {
    var link = savedLink(PARENT, null);
    savedLink(OTHER_PARENT, SUSPENDED);

    assertThat(
            repository.findByChildPersonalCodeAndStatusAndSuspendedAtIsNullAndValidUntilAfter(
                CHILD, ACTIVE, EIGHTEENTH_BIRTHDAY.minusDays(1)))
        .containsExactly(link);
  }

  @Test
  void doesNotFindExpiredParentOfChild() {
    savedLink(PARENT, null);

    assertThat(
            repository.findByChildPersonalCodeAndStatusAndSuspendedAtIsNullAndValidUntilAfter(
                CHILD, ACTIVE, EIGHTEENTH_BIRTHDAY))
        .isEmpty();
  }

  @Test
  void findsEveryActiveLinkRegardlessOfParent() {
    // the AML backfill enumerates the whole active population through this query
    var link = savedLink(PARENT, null);
    var otherLink = savedLink(OTHER_PARENT, null);

    assertThat(
            repository.findByStatusAndSuspendedAtIsNullAndValidUntilAfter(
                ACTIVE, EIGHTEENTH_BIRTHDAY.minusDays(1)))
        .containsExactlyInAnyOrder(link, otherLink);
  }

  @Test
  void findsNoPendingOrSuspendedLinkRegardlessOfParent() {
    savedPendingLink(PARENT);
    savedLink(OTHER_PARENT, SUSPENDED);

    assertThat(
            repository.findByStatusAndSuspendedAtIsNullAndValidUntilAfter(
                ACTIVE, EIGHTEENTH_BIRTHDAY.minusDays(1)))
        .isEmpty();
  }

  @Test
  void findsNoActiveLinkOnValidUntilDate() {
    savedLink(PARENT, null);

    assertThat(
            repository.findByStatusAndSuspendedAtIsNullAndValidUntilAfter(
                ACTIVE, EIGHTEENTH_BIRTHDAY))
        .isEmpty();
  }
}
