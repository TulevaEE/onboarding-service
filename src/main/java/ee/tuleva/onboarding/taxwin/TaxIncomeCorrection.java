package ee.tuleva.onboarding.taxwin;

import static jakarta.persistence.GenerationType.IDENTITY;
import static lombok.AccessLevel.PROTECTED;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Map;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

@Entity
@Table(name = "tax_income_correction")
@NoArgsConstructor(access = PROTECTED)
class TaxIncomeCorrection {

  @Id
  @GeneratedValue(strategy = IDENTITY)
  private @Nullable Long id;

  private Long userId;

  private LocalDate incomeMonth;

  private BigDecimal amount;

  TaxIncomeCorrection(Long userId, YearMonth month, BigDecimal amount) {
    this.userId = userId;
    this.incomeMonth = month.atDay(1);
    this.amount = amount;
  }

  Map.Entry<YearMonth, BigDecimal> asEntry() {
    return Map.entry(YearMonth.from(incomeMonth), amount);
  }

  void correctTo(BigDecimal amount) {
    this.amount = amount;
  }
}
