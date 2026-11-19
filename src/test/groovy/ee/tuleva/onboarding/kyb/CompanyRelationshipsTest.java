package ee.tuleva.onboarding.kyb;

import static ee.tuleva.onboarding.kyb.OwnershipRole.BENEFICIAL_OWNER;
import static ee.tuleva.onboarding.kyb.OwnershipRole.SHAREHOLDER;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.ariregister.CompanyRelationship;
import java.time.LocalDate;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class CompanyRelationshipsTest {

  private static final CompanyRelationship BOARD_MEMBER =
      relationship("JUHL", LocalDate.of(2017, 4, 3), null);
  private static final CompanyRelationship FORMER_SHAREHOLDER =
      relationship("OSAN", LocalDate.of(2020, 1, 15), LocalDate.of(2025, 12, 31));
  private static final CompanyRelationship CURRENT_SHAREHOLDER =
      relationship("OSAN", LocalDate.of(2018, 6, 1), null);
  private static final CompanyRelationship UNDATED_OWNER = relationship("W", null, null);

  private static final List<CompanyRelationship> REGISTERED =
      List.of(BOARD_MEMBER, FORMER_SHAREHOLDER, CURRENT_SHAREHOLDER, UNDATED_OWNER);

  @Test
  void activeLeavesOutRelationshipsThatEndedBeforeTheDay() {
    var relationships = new CompanyRelationships(REGISTERED, LocalDate.of(2026, 3, 1));

    assertThat(relationships.active())
        .containsExactly(BOARD_MEMBER, CURRENT_SHAREHOLDER, UNDATED_OWNER);
  }

  @Test
  void activeLeavesOutRelationshipsThatStartAfterTheDay() {
    var relationships = new CompanyRelationships(REGISTERED, LocalDate.of(2017, 6, 1));

    assertThat(relationships.active()).containsExactly(BOARD_MEMBER, UNDATED_OWNER);
  }

  @Test
  void activeKeepsRelationshipsStartingOrEndingOnTheDay() {
    var relationships = new CompanyRelationships(REGISTERED, LocalDate.of(2025, 12, 31));

    assertThat(relationships.active())
        .containsExactly(BOARD_MEMBER, FORMER_SHAREHOLDER, CURRENT_SHAREHOLDER, UNDATED_OWNER);
    assertThat(new CompanyRelationships(REGISTERED, LocalDate.of(2018, 6, 1)).active())
        .containsExactly(BOARD_MEMBER, CURRENT_SHAREHOLDER, UNDATED_OWNER);
  }

  @Test
  void ownershipsAreEveryShareholdingAndBeneficialOwnershipWhateverItsDates() {
    var nasdaqShareholder = relationship("O", LocalDate.of(2021, 2, 1), null);
    var relationships =
        new CompanyRelationships(
            List.of(
                BOARD_MEMBER,
                FORMER_SHAREHOLDER,
                CURRENT_SHAREHOLDER,
                UNDATED_OWNER,
                nasdaqShareholder),
            LocalDate.of(2026, 3, 1));

    assertThat(relationships.ownerships())
        .containsExactly(
            new KybOwnership(
                "38501010002", SHAREHOLDER, LocalDate.of(2020, 1, 15), LocalDate.of(2025, 12, 31)),
            new KybOwnership("38501010002", SHAREHOLDER, LocalDate.of(2018, 6, 1), null),
            new KybOwnership("38501010002", BENEFICIAL_OWNER, null, null),
            new KybOwnership("38501010002", SHAREHOLDER, LocalDate.of(2021, 2, 1), null));
  }

  private static CompanyRelationship relationship(
      String roleCode, @Nullable LocalDate startDate, @Nullable LocalDate endDate) {
    return new CompanyRelationship(
        "F",
        roleCode,
        roleCode,
        "Jaan",
        "Tamm",
        "38501010002",
        null,
        startDate,
        endDate,
        null,
        null,
        "EST");
  }
}
