package ee.tuleva.onboarding.taxwin;

import static jakarta.persistence.GenerationType.IDENTITY;
import static lombok.AccessLevel.PROTECTED;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

@Entity
@Table(name = "tax_expected_yearly_income")
@NoArgsConstructor(access = PROTECTED)
class TaxExpectedYearlyIncome {

  @Id
  @GeneratedValue(strategy = IDENTITY)
  private @Nullable Long id;

  private Long userId;

  private int incomeYear;

  private BigDecimal amount;

  TaxExpectedYearlyIncome(Long userId, YearlyIncome yearlyIncome) {
    this.userId = userId;
    this.incomeYear = yearlyIncome.year();
    this.amount = yearlyIncome.amount();
  }

  YearlyIncome asYearlyIncome() {
    return new YearlyIncome(incomeYear, amount);
  }

  void expect(YearlyIncome yearlyIncome) {
    this.incomeYear = yearlyIncome.year();
    this.amount = yearlyIncome.amount();
  }
}
