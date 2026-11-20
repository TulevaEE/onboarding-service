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
@Table(name = "tax_income_month")
@NoArgsConstructor(access = PROTECTED)
class TaxIncomeMonth {

  @Id
  @GeneratedValue(strategy = IDENTITY)
  private @Nullable Long id;

  private Long userId;

  private LocalDate incomeMonth;

  private BigDecimal income;

  private BigDecimal withheldContributions;

  TaxIncomeMonth(Long userId, YearMonth month, DeclaredMonth declared) {
    this.userId = userId;
    this.incomeMonth = month.atDay(1);
    this.income = declared.income();
    this.withheldContributions = declared.withheldContributions();
  }

  Map.Entry<YearMonth, DeclaredMonth> asEntry() {
    return Map.entry(YearMonth.from(incomeMonth), new DeclaredMonth(income, withheldContributions));
  }
}
