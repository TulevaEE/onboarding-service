package ee.tuleva.onboarding.investment.position;

import static jakarta.persistence.EnumType.STRING;

import ee.tuleva.onboarding.tulevafund.TulevaFund;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

@Data
@Builder
@Entity
@Table(name = "investment_fund_position")
@AllArgsConstructor
@NoArgsConstructor
public class FundPosition {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @NotNull private LocalDate navDate;

  private @Nullable LocalDate reportDate;

  @NotNull
  @Enumerated(STRING)
  @Column(name = "fund_code")
  private TulevaFund fund;

  @NotNull
  @Enumerated(STRING)
  private AccountType accountType;

  @NotNull private String accountName;

  private @Nullable String accountId;

  private @Nullable BigDecimal quantity;

  private @Nullable BigDecimal marketPrice;

  private @Nullable String currency;

  private @Nullable BigDecimal marketValue;

  private Instant createdAt;

  private Instant updatedAt;

  private static final List<String> TRADE_PAYABLE_ACCOUNT_NAMES =
      List.of("payables of unsettled transactions", "Trade Settlement Payable");

  private static final String REDEMPTION_PAYABLE_ACCOUNT_NAME = "Payables of redeemed units";

  public boolean isTradePayable() {
    return accountName != null
        && TRADE_PAYABLE_ACCOUNT_NAMES.stream().anyMatch(accountName::contains);
  }

  public boolean isRedemptionPayableOf(TulevaFund redeemedFund) {
    return accountName != null
        && accountName.contains(REDEMPTION_PAYABLE_ACCOUNT_NAME)
        && redeemedFund.getIsin().equals(accountId);
  }
}
