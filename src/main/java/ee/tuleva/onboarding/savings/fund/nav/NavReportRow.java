package ee.tuleva.onboarding.savings.fund.nav;

import static ee.tuleva.onboarding.currency.Currency.EUR;
import static jakarta.persistence.EnumType.STRING;
import static jakarta.persistence.GenerationType.IDENTITY;

import ee.tuleva.onboarding.currency.Currency;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@Entity
@Table(name = "nav_report")
@AllArgsConstructor
@NoArgsConstructor
class NavReportRow {

  @Id
  @GeneratedValue(strategy = IDENTITY)
  private Long id;

  @NotNull private LocalDate navDate;

  @NotNull private String fundCode;

  @NotNull private String accountType;

  @NotNull private String accountName;

  private String accountId;

  private BigDecimal quantity;

  private BigDecimal marketPrice;

  @Enumerated(STRING)
  @Builder.Default
  private Currency currency = EUR;

  private BigDecimal marketValue;

  private Instant createdAt;
}
