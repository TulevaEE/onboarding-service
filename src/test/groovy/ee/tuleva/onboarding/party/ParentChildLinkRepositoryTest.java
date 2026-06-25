package ee.tuleva.onboarding.party;

import static ee.tuleva.onboarding.party.RepresentationType.LEGAL_REPRESENTATIVE;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

@DataJpaTest
class ParentChildLinkRepositoryTest {

  @Autowired ParentChildLinkRepository repository;

  private static final String PARENT = "38001010000";
  private static final String CHILD = "61001010000";
  private static final LocalDate EIGHTEENTH_BIRTHDAY = LocalDate.of(2030, 1, 1);
  private static final Instant SUSPENDED = Instant.parse("2026-05-22T00:00:00Z");

  private ParentChildLink savedLink(Instant suspendedAt) {
    return repository.save(
        ParentChildLink.builder()
            .parentPersonalCode(PARENT)
            .childPersonalCode(CHILD)
            .relationshipType(LEGAL_REPRESENTATIVE)
            .validUntil(EIGHTEENTH_BIRTHDAY)
            .suspendedAt(suspendedAt)
            .build());
  }

  @Test
  void findsActiveLinkBeforeValidUntil() {
    var link = savedLink(null);

    assertThat(
            repository.findByParentPersonalCodeAndSuspendedAtIsNullAndValidUntilAfter(
                PARENT, EIGHTEENTH_BIRTHDAY.minusDays(1)))
        .containsExactly(link);
  }

  @Test
  void doesNotFindLinkOnValidUntilDate() {
    savedLink(null);

    assertThat(
            repository.findByParentPersonalCodeAndSuspendedAtIsNullAndValidUntilAfter(
                PARENT, EIGHTEENTH_BIRTHDAY))
        .isEmpty();
  }

  @Test
  void doesNotFindLinkAfterValidUntil() {
    savedLink(null);

    assertThat(
            repository.findByParentPersonalCodeAndSuspendedAtIsNullAndValidUntilAfter(
                PARENT, EIGHTEENTH_BIRTHDAY.plusDays(1)))
        .isEmpty();
  }

  @Test
  void doesNotFindSuspendedLink() {
    savedLink(SUSPENDED);

    assertThat(
            repository.findByParentPersonalCodeAndSuspendedAtIsNullAndValidUntilAfter(
                PARENT, EIGHTEENTH_BIRTHDAY.minusDays(1)))
        .isEmpty();
  }

  @Test
  void representsActiveChildBeforeValidUntil() {
    savedLink(null);

    assertThat(
            repository
                .existsByParentPersonalCodeAndChildPersonalCodeAndSuspendedAtIsNullAndValidUntilAfter(
                    PARENT, CHILD, EIGHTEENTH_BIRTHDAY.minusDays(1)))
        .isTrue();
  }

  @Test
  void doesNotRepresentOnValidUntilDate() {
    savedLink(null);

    assertThat(
            repository
                .existsByParentPersonalCodeAndChildPersonalCodeAndSuspendedAtIsNullAndValidUntilAfter(
                    PARENT, CHILD, EIGHTEENTH_BIRTHDAY))
        .isFalse();
  }

  @Test
  void doesNotRepresentSuspendedChild() {
    savedLink(SUSPENDED);

    assertThat(
            repository
                .existsByParentPersonalCodeAndChildPersonalCodeAndSuspendedAtIsNullAndValidUntilAfter(
                    PARENT, CHILD, EIGHTEENTH_BIRTHDAY.minusDays(1)))
        .isFalse();
  }

  @Test
  void doesNotRepresentUnknownChild() {
    savedLink(null);

    assertThat(
            repository
                .existsByParentPersonalCodeAndChildPersonalCodeAndSuspendedAtIsNullAndValidUntilAfter(
                    PARENT, "99999999999", EIGHTEENTH_BIRTHDAY.minusDays(1)))
        .isFalse();
  }
}
