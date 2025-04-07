package ee.tuleva.onboarding.analytics.fund;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(
    name = "fund_transaction",
    schema = "public",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "fund_transaction_unique_key",
          columnNames = {
            "transaction_date",
            "personal_id",
            "transaction_type",
            "amount",
            "unit_amount"
          })
    })
public class FundTransaction {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private LocalDate transactionDate;
  private String isin;
  private String personName;
  private String personalId;
  private String pensionAccount;
  private String country;
  private String transactionType;
  private String purpose;
  private String applicationType;
  private BigDecimal unitAmount;
  private BigDecimal price;
  private BigDecimal nav;
  private BigDecimal amount;
  private BigDecimal serviceFee;
  private LocalDateTime dateCreated;
}
