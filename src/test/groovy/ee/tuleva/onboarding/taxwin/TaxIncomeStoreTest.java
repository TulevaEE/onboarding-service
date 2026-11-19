package ee.tuleva.onboarding.taxwin;

import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.user.User;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

@DataJpaTest
class TaxIncomeStoreTest {

  private static final YearlyIncome YEARLY_INCOME =
      new YearlyIncome(2026, new BigDecimal("33000.00"));
  private static final Map<YearMonth, BigDecimal> SPREAD =
      Map.of(
          YearMonth.of(2026, 11), new BigDecimal("4000.00"),
          YearMonth.of(2026, 12), new BigDecimal("4000.00"));

  @Autowired private TestEntityManager entityManager;
  @Autowired private TaxIncomeMonthRepository incomeMonthRepository;
  @Autowired private TaxIncomeCorrectionRepository correctionRepository;
  @Autowired private TaxExpectedYearlyIncomeRepository yearlyIncomeRepository;

  private TaxIncomeStore incomeStore;
  private Long userId;

  @BeforeEach
  void setUp() {
    incomeStore =
        new TaxIncomeStore(incomeMonthRepository, correctionRepository, yearlyIncomeRepository);
    userId = user().getId();
  }

  @Test
  void remembersTheYearlyIncomeAlongWithTheMonthsItWasSpreadOver() {
    incomeStore.expectYearlyIncome(userId, YEARLY_INCOME, SPREAD);
    entityManager.flush();
    entityManager.clear();

    assertThat(incomeStore.yearlyIncome(userId)).contains(YEARLY_INCOME);
    assertThat(incomeStore.corrections(userId)).isEqualTo(SPREAD);
  }

  @Test
  void aNewYearlyIncomeReplacesTheOldOne() {
    incomeStore.expectYearlyIncome(userId, YEARLY_INCOME, SPREAD);
    var higher = new YearlyIncome(2026, new BigDecimal("35000.00"));
    var higherSpread =
        Map.of(
            YearMonth.of(2026, 11), new BigDecimal("5000.00"),
            YearMonth.of(2026, 12), new BigDecimal("5000.00"));

    incomeStore.expectYearlyIncome(userId, higher, higherSpread);
    entityManager.flush();
    entityManager.clear();

    assertThat(incomeStore.yearlyIncome(userId)).contains(higher);
    assertThat(incomeStore.corrections(userId)).isEqualTo(higherSpread);
  }

  @Test
  void changingOneMonthForgetsTheYearlyIncomeButKeepsTheOtherMonths() {
    incomeStore.expectYearlyIncome(userId, YEARLY_INCOME, SPREAD);

    incomeStore.correct(userId, YearMonth.of(2026, 12), new BigDecimal("6000.00"));
    entityManager.flush();
    entityManager.clear();

    assertThat(incomeStore.yearlyIncome(userId)).isEmpty();
    assertThat(incomeStore.corrections(userId))
        .isEqualTo(
            Map.of(
                YearMonth.of(2026, 11), new BigDecimal("4000.00"),
                YearMonth.of(2026, 12), new BigDecimal("6000.00")));
  }

  @Test
  void goingBackToTheEstimateForAMonthForgetsTheYearlyIncome() {
    incomeStore.expectYearlyIncome(userId, YEARLY_INCOME, SPREAD);

    incomeStore.removeCorrection(userId, YearMonth.of(2026, 12));
    entityManager.flush();
    entityManager.clear();

    assertThat(incomeStore.yearlyIncome(userId)).isEmpty();
    assertThat(incomeStore.corrections(userId))
        .isEqualTo(Map.of(YearMonth.of(2026, 11), new BigDecimal("4000.00")));
  }

  @Test
  void forgettingTheYearlyIncomeForgetsTheMonthsItWasSpreadOver() {
    incomeStore.expectYearlyIncome(userId, YEARLY_INCOME, SPREAD);

    incomeStore.forgetYearlyIncome(userId);
    entityManager.flush();
    entityManager.clear();

    assertThat(incomeStore.yearlyIncome(userId)).isEmpty();
    assertThat(incomeStore.corrections(userId)).isEmpty();
  }

  @Test
  void deletingThePersonsIncomeDeletesTheYearlyIncomeToo() {
    incomeStore.expectYearlyIncome(userId, YEARLY_INCOME, SPREAD);

    incomeStore.deleteAll(userId);
    entityManager.flush();
    entityManager.clear();

    assertThat(incomeStore.yearlyIncome(userId)).isEmpty();
    assertThat(incomeStore.corrections(userId)).isEmpty();
  }

  private User user() {
    User user = new User();
    user.setPersonalCode("38001085718");
    user.setFirstName("Näidis");
    user.setLastName("Isik");
    return entityManager.persist(user);
  }
}
