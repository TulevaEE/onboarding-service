package ee.tuleva.onboarding.swedbank.reconcillation;

import static ee.tuleva.onboarding.savings.fund.SavingFundPayment.Status.RETURNED;
import static ee.tuleva.onboarding.savings.fund.SavingFundPayment.Status.TO_BE_RETURNED;
import static ee.tuleva.onboarding.swedbank.statement.BankStatementBalance.StatementBalanceType.CLOSE;
import static java.math.BigDecimal.ZERO;

import ee.tuleva.onboarding.ledger.LedgerService;
import ee.tuleva.onboarding.ledger.SavingsFundLedger;
import ee.tuleva.onboarding.savings.fund.SavingFundPayment;
import ee.tuleva.onboarding.savings.fund.SavingFundPaymentRepository;
import ee.tuleva.onboarding.swedbank.fetcher.SwedbankAccountConfiguration;
import ee.tuleva.onboarding.swedbank.fetcher.SwedbankStatementFetcher.SwedbankAccount;
import ee.tuleva.onboarding.swedbank.statement.BankStatement;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class Reconciliator {

  private final LedgerService ledgerService;
  private final SavingFundPaymentRepository paymentRepository;
  private final SavingsFundLedger savingsFundLedger;
  private final SwedbankAccountConfiguration swedbankAccountConfiguration;

  @Transactional
  public void reconcile(BankStatement bankStatement) {
    detectAndFixMissingLedgerEntries();
    checkBalanceMatch(bankStatement);
  }

  void detectAndFixMissingLedgerEntries() {
    log.info("Starting detection of missing ledger entries");

    // 1. Find and fix unattributed payments without ledger entries
    List<SavingFundPayment> unrecordedUnattributed = findUnrecordedUnattributedPayments();
    if (!unrecordedUnattributed.isEmpty()) {
      log.info("Found {} potential unattributed payments to check", unrecordedUnattributed.size());

      int created = 0;
      for (SavingFundPayment payment : unrecordedUnattributed) {
        if (!savingsFundLedger.hasLedgerEntry(payment.getId())) {
          try {
            log.info(
                "Recording unattributed payment in ledger: paymentId={}, amount={}",
                payment.getId(),
                payment.getAmount());
            savingsFundLedger.recordUnattributedPayment(payment.getAmount(), payment.getId());
            created++;
          } catch (Exception e) {
            // This might happen if the entry was created between the check and creation
            log.info(
                "Failed to record unattributed payment (may already exist): paymentId={}",
                payment.getId(),
                e);
          }
        }
      }
      if (created > 0) {
        log.warn("Created {} missing unattributed payment ledger entries", created);
      }
    }

    // 2. Find and fix bounce backs without ledger entries
    List<SavingFundPayment> unbouncedReturns = findUnbouncedReturns();
    if (!unbouncedReturns.isEmpty()) {
      log.info("Found {} potential return payments to check", unbouncedReturns.size());

      int created = 0;
      for (SavingFundPayment returnPayment : unbouncedReturns) {
        if (!savingsFundLedger.hasLedgerEntry(returnPayment.getId())) {
          try {
            log.info(
                "Recording bounce back in ledger: paymentId={}, amount={}, beneficiaryIban={}",
                returnPayment.getId(),
                returnPayment.getAmount(),
                returnPayment.getBeneficiaryIban());
            // Note: amount is already negative for outgoing payments, so we negate it to make it
            // positive
            savingsFundLedger.bounceBackUnattributedPayment(
                returnPayment.getAmount().negate(), returnPayment.getId());
            created++;
          } catch (Exception e) {
            // This might happen if the entry was created between the check and creation
            log.info(
                "Failed to record bounce back (may already exist): paymentId={}",
                returnPayment.getId(),
                e);
          }
        }
      }
      if (created > 0) {
        log.warn("Created {} missing bounce back ledger entries", created);
      }
    }

    log.info("Completed detection of missing ledger entries");
  }

  private List<SavingFundPayment> findUnrecordedUnattributedPayments() {
    // Find payments that were marked TO_BE_RETURNED or RETURNED
    // These should have unattributed ledger entries
    List<SavingFundPayment> unverifiedPayments = new ArrayList<>();
    unverifiedPayments.addAll(paymentRepository.findPaymentsWithStatus(TO_BE_RETURNED));
    unverifiedPayments.addAll(paymentRepository.findPaymentsWithStatus(RETURNED));
    return unverifiedPayments;
  }

  private List<SavingFundPayment> findUnbouncedReturns() {
    // Find outgoing return payments (negative amount, not to investment account)
    // These should have bounce back ledger entries
    List<SavingFundPayment> allPayments = paymentRepository.findAll();

    String investmentIban =
        swedbankAccountConfiguration.getAccountIban(SwedbankAccount.INVESTMENT_EUR).orElse("");

    return allPayments.stream()
        .filter(payment -> payment.getAmount().compareTo(ZERO) < 0) // outgoing
        .filter(
            payment ->
                !investmentIban.equals(payment.getBeneficiaryIban())) // not to investment account
        .toList();
  }

  private void checkBalanceMatch(BankStatement bankStatement) {
    var closingBankBalance =
        bankStatement.getBalances().stream()
            .filter(balance -> balance.type().equals(CLOSE))
            .findFirst()
            .orElseThrow();

    var bankBalanceTime =
        closingBankBalance
            .time()
            .atStartOfDay(ZoneId.of("Europe/Tallinn"))
            .with(LocalTime.MAX)
            .toInstant();

    var bankStatementAccount = bankStatement.getBankStatementAccount().getBankAccountType();

    var ledgerSystemAccount = bankStatementAccount.getLedgerAccount();
    var ledgerAccountBalance =
        ledgerService.getSystemAccount(ledgerSystemAccount).getBalanceAt(bankBalanceTime);

    log.info(
        "Reconciling: bankAccount={}, closingBalance={}, ledgerAccount={}, ledgerBalance={}",
        bankStatementAccount,
        closingBankBalance.balance(),
        ledgerSystemAccount,
        ledgerAccountBalance);

    if (ledgerAccountBalance.compareTo(closingBankBalance.balance()) != 0) {
      throw new IllegalStateException(
          "Bank statement reconciliation failed: bankAccount=%s, closingBalance=%s, ledgerAccount=%s, ledgerBalance=%s"
              .formatted(
                  bankStatementAccount,
                  closingBankBalance.balance(),
                  ledgerSystemAccount,
                  ledgerAccountBalance));
    }
  }
}
