package ee.tuleva.onboarding.investment.risk;

import static ee.tuleva.onboarding.fund.TulevaFund.TKF100;
import static ee.tuleva.onboarding.fund.TulevaFund.TUK75;
import static ee.tuleva.onboarding.investment.risk.RiskIndicatorType.SRI;
import static ee.tuleva.onboarding.investment.risk.RiskIndicatorType.SRRI;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

@DataJpaTest
class DisclosedRiskIndicatorRepositoryIT {

  @Autowired private DisclosedRiskIndicatorRepository repository;

  @Test
  void theMigrationSeedsTheClassesCurrentlyPrintedInTheFiledDocuments() {
    var tuk75 = disclosureFor(SRRI, TUK75, LocalDate.of(2026, 8, 10));
    var tkf100 = disclosureFor(SRI, TKF100, LocalDate.of(2026, 8, 10));

    assertThat(tuk75.getDisclosedClass()).isEqualTo(5);
    assertThat(tuk75.getDisclosedFrom()).isEqualTo(LocalDate.of(2026, 3, 19));
    assertThat(tkf100.getDisclosedClass()).isEqualTo(4);
    assertThat(tkf100.getDisclosedFrom()).isEqualTo(LocalDate.of(2026, 2, 27));
  }

  @Test
  void aNewerDocumentVersionSupersedesTheOlderOneWithoutDeletingIt() {
    repository.saveAndFlush(
        DisclosedRiskIndicator.builder()
            .indicatorType(SRRI)
            .fund(TUK75)
            .disclosedClass(6)
            .disclosedFrom(LocalDate.of(2027, 3, 19))
            .document("Pohiteave TUK75 19.03.2027")
            .build());

    assertThat(disclosureFor(SRRI, TUK75, LocalDate.of(2027, 6, 1)).getDisclosedClass())
        .isEqualTo(6);
    assertThat(disclosureFor(SRRI, TUK75, LocalDate.of(2026, 12, 1)).getDisclosedClass())
        .isEqualTo(5);
  }

  @Test
  void aDateBeforeAnyFiledDocumentHasNoDisclosedClass() {
    var found =
        repository
            .findFirstByIndicatorTypeAndFundAndDisclosedFromLessThanEqualOrderByDisclosedFromDesc(
                SRI, TKF100, LocalDate.of(2020, 1, 1));

    assertThat(found).isEmpty();
  }

  private DisclosedRiskIndicator disclosureFor(
      RiskIndicatorType indicatorType, ee.tuleva.onboarding.fund.TulevaFund fund, LocalDate asOf) {
    return repository
        .findFirstByIndicatorTypeAndFundAndDisclosedFromLessThanEqualOrderByDisclosedFromDesc(
            indicatorType, fund, asOf)
        .orElseThrow();
  }
}
