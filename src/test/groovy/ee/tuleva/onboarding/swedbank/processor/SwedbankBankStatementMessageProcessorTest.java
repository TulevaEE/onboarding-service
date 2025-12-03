package ee.tuleva.onboarding.swedbank.processor;

import static ee.tuleva.onboarding.auth.UserFixture.sampleUser;
import static ee.tuleva.onboarding.savings.fund.SavingFundPayment.Status.RETURNED;
import static ee.tuleva.onboarding.savings.fund.SavingFundPayment.Status.TO_BE_RETURNED;
import static ee.tuleva.onboarding.savings.fund.SavingFundPaymentFixture.aPayment;
import static ee.tuleva.onboarding.swedbank.statement.BankAccountType.DEPOSIT_EUR;
import static ee.tuleva.onboarding.swedbank.statement.BankAccountType.FUND_INVESTMENT_EUR;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ee.tuleva.onboarding.ledger.SavingsFundLedger;
import ee.tuleva.onboarding.savings.fund.SavingFundPayment;
import ee.tuleva.onboarding.savings.fund.SavingFundPaymentExtractor;
import ee.tuleva.onboarding.savings.fund.SavingFundPaymentRepository;
import ee.tuleva.onboarding.savings.fund.SavingFundPaymentUpsertionService;
import ee.tuleva.onboarding.swedbank.fetcher.SwedbankAccountConfiguration;
import ee.tuleva.onboarding.swedbank.statement.BankStatement;
import ee.tuleva.onboarding.swedbank.statement.BankStatement.BankStatementType;
import ee.tuleva.onboarding.swedbank.statement.BankStatementAccount;
import ee.tuleva.onboarding.swedbank.statement.SwedbankBankStatementExtractor;
import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.user.UserRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SwedbankBankStatementMessageProcessorTest {

  private static final String DEPOSIT_ACCOUNT_IBAN = "EE442200221092874625";
  private static final String FUND_INVESTMENT_IBAN = "EE552200221055544433";
  private static final String EXTERNAL_ACCOUNT_IBAN = "EE112233445566778899";

  @Mock SwedbankBankStatementExtractor swedbankBankStatementExtractor;
  @Mock SavingFundPaymentExtractor paymentExtractor;
  @Mock SavingFundPaymentUpsertionService paymentService;
  @Mock SwedbankAccountConfiguration swedbankAccountConfiguration;
  @Mock SavingsFundLedger savingsFundLedger;
  @Mock SavingFundPaymentRepository savingFundPaymentRepository;
  @Mock UserRepository userRepository;
  @InjectMocks SwedbankBankStatementMessageProcessor processor;

  @Test
  @DisplayName("Outgoing to fund account creates ledger transfer entry")
  void outgoingToFundAccount_createsLedgerTransferEntry() {
    var outgoingPayment =
        aPayment().amount(new BigDecimal("-100.00")).beneficiaryIban(FUND_INVESTMENT_IBAN).build();
    setupMocksForPayment(outgoingPayment);
    when(swedbankAccountConfiguration.getAccountType(FUND_INVESTMENT_IBAN))
        .thenReturn(FUND_INVESTMENT_EUR);

    processor.processMessage("<xml>", SwedbankMessageType.INTRA_DAY_REPORT);

    verify(savingsFundLedger).transferToFundAccount(new BigDecimal("100.00"));
  }

  @Test
  @DisplayName(
      "Outgoing return with user-cancelled original creates payment cancelled ledger entry")
  void outgoingReturn_userCancelled_createsPaymentCancelledLedgerEntry() {
    User user = sampleUser().build();
    var originalPaymentId = UUID.randomUUID();
    var endToEndId = originalPaymentId.toString().replace("-", "");
    var originalPayment =
        aPayment()
            .id(originalPaymentId)
            .userId(user.getId())
            .amount(new BigDecimal("50.00"))
            .remitterIban(EXTERNAL_ACCOUNT_IBAN)
            .status(RETURNED)
            .build();
    var returnPayment =
        aPayment()
            .amount(new BigDecimal("-50.00"))
            .beneficiaryIban(EXTERNAL_ACCOUNT_IBAN)
            .endToEndId(endToEndId)
            .build();
    setupMocksForPayment(returnPayment);
    when(swedbankAccountConfiguration.getAccountType(EXTERNAL_ACCOUNT_IBAN)).thenReturn(null);
    when(savingFundPaymentRepository.findOriginalPaymentForReturn(endToEndId))
        .thenReturn(Optional.of(originalPayment));
    when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

    processor.processMessage("<xml>", SwedbankMessageType.INTRA_DAY_REPORT);

    verify(savingsFundLedger)
        .recordPaymentCancelled(user, new BigDecimal("50.00"), originalPaymentId);
  }

  @Test
  @DisplayName("Outgoing return with unattributed original creates bounce back ledger entry")
  void outgoingReturn_unattributed_createsBounceBackLedgerEntry() {
    var originalPaymentId = UUID.randomUUID();
    var endToEndId = originalPaymentId.toString().replace("-", "");
    var originalPayment =
        aPayment()
            .id(originalPaymentId)
            .userId(null)
            .amount(new BigDecimal("75.00"))
            .remitterIban(EXTERNAL_ACCOUNT_IBAN)
            .status(TO_BE_RETURNED)
            .build();
    var returnPayment =
        aPayment()
            .amount(new BigDecimal("-75.00"))
            .beneficiaryIban(EXTERNAL_ACCOUNT_IBAN)
            .endToEndId(endToEndId)
            .build();
    setupMocksForPayment(returnPayment);
    when(swedbankAccountConfiguration.getAccountType(EXTERNAL_ACCOUNT_IBAN)).thenReturn(null);
    when(savingFundPaymentRepository.findOriginalPaymentForReturn(endToEndId))
        .thenReturn(Optional.of(originalPayment));

    processor.processMessage("<xml>", SwedbankMessageType.INTRA_DAY_REPORT);

    verify(savingsFundLedger)
        .bounceBackUnattributedPayment(new BigDecimal("75.00"), originalPaymentId);
  }

  @Test
  @DisplayName("Outgoing return with no original found does not create ledger entry")
  void outgoingReturn_originalNotFound_doesNotCreateLedgerEntry() {
    var returnPayment =
        aPayment()
            .amount(new BigDecimal("-100.00"))
            .beneficiaryIban(EXTERNAL_ACCOUNT_IBAN)
            .endToEndId("nonexistent12345678901234567890")
            .build();
    setupMocksForPayment(returnPayment);
    when(swedbankAccountConfiguration.getAccountType(EXTERNAL_ACCOUNT_IBAN)).thenReturn(null);
    when(savingFundPaymentRepository.findOriginalPaymentForReturn(any()))
        .thenReturn(Optional.empty());

    processor.processMessage("<xml>", SwedbankMessageType.INTRA_DAY_REPORT);

    verify(savingsFundLedger, never()).recordPaymentCancelled(any(), any(), any());
    verify(savingsFundLedger, never()).bounceBackUnattributedPayment(any(), any());
  }

  @Test
  @DisplayName("User-cancelled return with user not found does not create ledger entry")
  void userCancelledReturn_userNotFound_doesNotCreateLedgerEntry() {
    Long missingUserId = 99999L;
    var originalPaymentId = UUID.randomUUID();
    var endToEndId = originalPaymentId.toString().replace("-", "");
    var originalPayment =
        aPayment()
            .id(originalPaymentId)
            .userId(missingUserId)
            .amount(new BigDecimal("50.00"))
            .remitterIban(EXTERNAL_ACCOUNT_IBAN)
            .status(RETURNED)
            .build();
    var returnPayment =
        aPayment()
            .amount(new BigDecimal("-50.00"))
            .beneficiaryIban(EXTERNAL_ACCOUNT_IBAN)
            .endToEndId(endToEndId)
            .build();
    setupMocksForPayment(returnPayment);
    when(swedbankAccountConfiguration.getAccountType(EXTERNAL_ACCOUNT_IBAN)).thenReturn(null);
    when(savingFundPaymentRepository.findOriginalPaymentForReturn(endToEndId))
        .thenReturn(Optional.of(originalPayment));
    when(userRepository.findById(missingUserId)).thenReturn(Optional.empty());

    processor.processMessage("<xml>", SwedbankMessageType.INTRA_DAY_REPORT);

    verify(savingsFundLedger, never()).recordPaymentCancelled(any(), any(), any());
  }

  @Test
  @DisplayName("Incoming payment does not create ledger entry in processor")
  void incomingPayment_doesNotCreateLedgerEntry() {
    var incomingPayment = aPayment().amount(new BigDecimal("200.00")).build();
    setupMocksForPayment(incomingPayment);

    processor.processMessage("<xml>", SwedbankMessageType.INTRA_DAY_REPORT);

    verify(savingsFundLedger, never()).transferToFundAccount(any());
    verify(savingsFundLedger, never()).recordPaymentCancelled(any(), any(), any());
    verify(savingsFundLedger, never()).bounceBackUnattributedPayment(any(), any());
  }

  private void setupMocksForPayment(SavingFundPayment payment) {
    var bankStatement =
        new BankStatement(
            BankStatementType.INTRA_DAY_REPORT,
            new BankStatementAccount(DEPOSIT_ACCOUNT_IBAN, "Tuleva Fondid AS", "14118923"),
            List.of(),
            List.of(),
            Instant.now());
    when(swedbankBankStatementExtractor.extractFromIntraDayReport(any())).thenReturn(bankStatement);
    when(swedbankAccountConfiguration.getAccountType(DEPOSIT_ACCOUNT_IBAN)).thenReturn(DEPOSIT_EUR);
    when(paymentExtractor.extractPayments(bankStatement)).thenReturn(List.of(payment));

    doAnswer(
            invocation -> {
              Consumer<SavingFundPayment> callback = invocation.getArgument(1);
              callback.accept(payment);
              return null;
            })
        .when(paymentService)
        .upsert(eq(payment), any());
  }
}
