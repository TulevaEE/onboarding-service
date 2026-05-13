package ee.tuleva.onboarding.investment.report.publishing.data;

import static jakarta.persistence.GenerationType.IDENTITY;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;

@Getter
@Entity
@Table(name = "nav_report")
class NavReportView {

  @Id
  @GeneratedValue(strategy = IDENTITY)
  private Long id;

  private LocalDate navDate;
  private String fundCode;
  private String accountType;
  private String accountName;
  private String accountId;
  private BigDecimal quantity;
  private BigDecimal marketPrice;
  private String currency;
  private BigDecimal marketValue;
  private UUID calculationId;
  private Instant publishedAt;

  protected NavReportView() {}
}
