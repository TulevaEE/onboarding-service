package ee.tuleva.onboarding.savings.fund.taxreport;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "investment_account")
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Data
public class InvestmentAccount {

  @Id
  @NotNull
  @Column(nullable = false, updatable = false)
  private String personalCode;

  @NotNull
  @Column(nullable = false)
  private String iban;

  @NotNull
  @Column(nullable = false, updatable = false)
  private Instant createdAt;

  @NotNull
  @Column(nullable = false)
  private Instant updatedAt;
}
