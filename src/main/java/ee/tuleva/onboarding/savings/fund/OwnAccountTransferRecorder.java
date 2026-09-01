package ee.tuleva.onboarding.savings.fund;

import static ee.tuleva.onboarding.tulevafund.TulevaFund.TKF100;
import static java.math.BigDecimal.ZERO;
import static java.util.Objects.requireNonNull;

import ee.tuleva.onboarding.banking.BankAccount;
import ee.tuleva.onboarding.banking.BankAccountType;
import ee.tuleva.onboarding.banking.BankAccounts;
import ee.tuleva.onboarding.ledger.InternalTransferLedger;
import ee.tuleva.onboarding.savings.SavingFundPayment;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
class OwnAccountTransferRecorder {

  private final BankAccounts bankAccounts;
  private final InternalTransferLedger internalTransferLedger;

  boolean recordOutgoingTransfer(SavingFundPayment payment, BankAccountType sourceType) {
    if (payment.getAmount().compareTo(ZERO) >= 0) {
      return false;
    }
    Optional<BankAccount> target =
        bankAccounts
            .find(payment.getBeneficiaryIban())
            .filter(account -> account.belongsTo(TKF100));
    if (target.isEmpty()) {
      return false;
    }
    var amount = payment.getAmount().negate();
    log.info(
        "Recording own account transfer between fund bank accounts: from={}, to={}, amount={}, paymentId={}",
        sourceType,
        target.get().type(),
        amount,
        payment.getId());
    internalTransferLedger.recordInternalTransfer(
        sourceType.getLedgerAccount(),
        target.get().type().getLedgerAccount(),
        amount,
        payment.getId(),
        payment.bookingDateOrThrow(),
        requireNonNull(
            payment.getDescription(), "Missing description: paymentId=" + payment.getId()));
    return true;
  }
}
