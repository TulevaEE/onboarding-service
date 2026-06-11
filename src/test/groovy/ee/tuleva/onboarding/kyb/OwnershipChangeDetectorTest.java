package ee.tuleva.onboarding.kyb;

import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.ariregister.CompanyRelationship;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class OwnershipChangeDetectorTest {

  private final OwnershipChangeDetector detector = new OwnershipChangeDetector();

  @Test
  void detectsChangeWhenAFormerOwnerHasExited() {
    var history =
        List.of(
            owner("38501010001", LocalDate.of(2010, 1, 1), LocalDate.of(2018, 1, 1)),
            owner("49001010001", LocalDate.of(2018, 1, 1), null));

    assertThat(detector.ownerChangedBeforeOnboarding(history)).isTrue();
  }

  @Test
  void noChangeWhenTheSoleOwnerNeverLeft() {
    var history = List.of(owner("38501010001", LocalDate.of(2010, 1, 1), null));

    assertThat(detector.ownerChangedBeforeOnboarding(history)).isFalse();
  }

  @Test
  void noChangeWhenAnOwnerRecordWasSplitButThePersonIsStillAnOwner() {
    var history =
        List.of(
            owner("38501010001", LocalDate.of(2010, 1, 1), LocalDate.of(2015, 1, 1)),
            owner("38501010001", LocalDate.of(2015, 1, 1), null));

    assertThat(detector.ownerChangedBeforeOnboarding(history)).isFalse();
  }

  @Test
  void detectsChangeWhenABeneficialOwnerHasExited() {
    var history =
        List.of(
            beneficialOwner("38501010001", LocalDate.of(2010, 1, 1), LocalDate.of(2020, 1, 1)),
            beneficialOwner("49001010001", LocalDate.of(2020, 1, 1), null));

    assertThat(detector.ownerChangedBeforeOnboarding(history)).isTrue();
  }

  @Test
  void noChangeWhenOnlyABoardMemberChanged() {
    var history =
        List.of(
            boardMember("38501010001", LocalDate.of(2010, 1, 1), LocalDate.of(2019, 1, 1)),
            boardMember("49001010001", LocalDate.of(2019, 1, 1), null),
            owner("37801010009", LocalDate.of(2010, 1, 1), null));

    assertThat(detector.ownerChangedBeforeOnboarding(history)).isFalse();
  }

  @Test
  void noChangeForEmptyHistory() {
    assertThat(detector.ownerChangedBeforeOnboarding(List.of())).isFalse();
  }

  private static CompanyRelationship owner(String personalCode, LocalDate start, LocalDate end) {
    return relationship("OSAN", personalCode, start, end, new BigDecimal("100.00"), null);
  }

  private static CompanyRelationship beneficialOwner(
      String personalCode, LocalDate start, LocalDate end) {
    return relationship(
        "OSAN", personalCode, start, end, new BigDecimal("100.00"), "Osaluse kaudu");
  }

  private static CompanyRelationship boardMember(
      String personalCode, LocalDate start, LocalDate end) {
    return relationship("JUHL", personalCode, start, end, null, null);
  }

  private static CompanyRelationship relationship(
      String roleCode,
      String personalCode,
      LocalDate startDate,
      LocalDate endDate,
      BigDecimal ownershipPercent,
      String controlMethod) {
    return new CompanyRelationship(
        "F",
        roleCode,
        roleCode,
        "First",
        "Last",
        personalCode,
        null,
        startDate,
        endDate,
        ownershipPercent,
        controlMethod,
        "EST");
  }
}
