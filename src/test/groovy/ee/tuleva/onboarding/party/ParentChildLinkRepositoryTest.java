package ee.tuleva.onboarding.party;

import static ee.tuleva.onboarding.party.RepresentationType.LEGAL_REPRESENTATIVE;
import static org.assertj.core.api.Assertions.assertThat;

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

  private ParentChildLink savedLink() {
    return repository.save(
        ParentChildLink.builder()
            .parentPersonalCode(PARENT)
            .childPersonalCode(CHILD)
            .relationshipType(LEGAL_REPRESENTATIVE)
            .validUntil(EIGHTEENTH_BIRTHDAY)
            .build());
  }

  @Test
  void findsActiveLinkBeforeValidUntil() {
    var link = savedLink();

    assertThat(
            repository.findByParentPersonalCodeAndValidUntilAfter(
                PARENT, EIGHTEENTH_BIRTHDAY.minusDays(1)))
        .containsExactly(link);
  }

  @Test
  void doesNotFindLinkOnValidUntilDate() {
    savedLink();

    assertThat(repository.findByParentPersonalCodeAndValidUntilAfter(PARENT, EIGHTEENTH_BIRTHDAY))
        .isEmpty();
  }

  @Test
  void doesNotFindLinkAfterValidUntil() {
    savedLink();

    assertThat(
            repository.findByParentPersonalCodeAndValidUntilAfter(
                PARENT, EIGHTEENTH_BIRTHDAY.plusDays(1)))
        .isEmpty();
  }

  @Test
  void representsActiveChildBeforeValidUntil() {
    savedLink();

    assertThat(
            repository.existsByParentPersonalCodeAndChildPersonalCodeAndValidUntilAfter(
                PARENT, CHILD, EIGHTEENTH_BIRTHDAY.minusDays(1)))
        .isTrue();
  }

  @Test
  void doesNotRepresentOnValidUntilDate() {
    savedLink();

    assertThat(
            repository.existsByParentPersonalCodeAndChildPersonalCodeAndValidUntilAfter(
                PARENT, CHILD, EIGHTEENTH_BIRTHDAY))
        .isFalse();
  }

  @Test
  void doesNotRepresentUnknownChild() {
    savedLink();

    assertThat(
            repository.existsByParentPersonalCodeAndChildPersonalCodeAndValidUntilAfter(
                PARENT, "99999999999", EIGHTEENTH_BIRTHDAY.minusDays(1)))
        .isFalse();
  }
}
