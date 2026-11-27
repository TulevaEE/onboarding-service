package ee.tuleva.onboarding.investment.fees.ocf;

import ee.tuleva.onboarding.ledger.BankCharges;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
record BankChargesCost(
    BigDecimal rate,
    @Nullable LocalDate windowStart,
    @Nullable BankCharges charges,
    List<OcfGap> gaps) {

  @Nullable BigDecimal bankFees() {
    return charges == null ? null : charges.bankFees();
  }

  @Nullable BigDecimal overdraftInterest() {
    return charges == null ? null : charges.overdraftInterest();
  }
}
