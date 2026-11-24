package ee.tuleva.onboarding.banking.seb.processor;

import static java.math.BigDecimal.ZERO;
import static java.util.function.Predicate.not;

import com.fasterxml.jackson.annotation.JsonProperty;
import ee.tuleva.onboarding.ledger.BookedManagementFeePayment;
import ee.tuleva.onboarding.ledger.UnderlyingFundFeeMoves;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NullMarked;

@Slf4j
@NullMarked
@RequiredArgsConstructor
public class UnderlyingFundFeeMoveService {

  private final PensionFundEntryClassifier classifier;
  private final UnderlyingFundFeeMoves underlyingFundFeeMoves;

  public record UnderlyingFundFeeMovePlan(
      TulevaFund fund, List<BookedManagementFeePayment> payments, int alreadyMoved) {

    @JsonProperty
    public int count() {
      return payments.size();
    }

    @JsonProperty
    public BigDecimal sum() {
      return payments.stream()
          .map(BookedManagementFeePayment::amount)
          .reduce(ZERO, BigDecimal::add);
    }
  }

  public UnderlyingFundFeeMovePlan preview(TulevaFund fund) {
    var underlyingFundFees = underlyingFundFeesBookedAsManagementFee(fund);
    var notYetMoved =
        underlyingFundFees.stream().filter(not(underlyingFundFeeMoves::isMoved)).toList();
    return new UnderlyingFundFeeMovePlan(
        fund, notYetMoved, underlyingFundFees.size() - notYetMoved.size());
  }

  public UnderlyingFundFeeMovePlan move(TulevaFund fund) {
    var plan = preview(fund);
    plan.payments().forEach(payment -> underlyingFundFeeMoves.move(fund, payment));
    log.info(
        "Moved underlying fund fees off the management fee: fund={}, moved={}, sum={}, alreadyMoved={}",
        fund,
        plan.count(),
        plan.sum(),
        plan.alreadyMoved());
    return plan;
  }

  private List<BookedManagementFeePayment> underlyingFundFeesBookedAsManagementFee(
      TulevaFund fund) {
    return underlyingFundFeeMoves.findManagementFeePayments(fund).stream()
        .filter(
            payment ->
                classifier.classifyManagementCompanyDebit(payment.bankDescription())
                    instanceof PensionFundEntryClassifier.UnderlyingFundManagementFeePayment)
        .toList();
  }
}
