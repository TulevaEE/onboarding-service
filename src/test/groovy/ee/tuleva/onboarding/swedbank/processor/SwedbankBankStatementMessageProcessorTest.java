package ee.tuleva.onboarding.swedbank.processor;

import static ee.tuleva.onboarding.auth.UserFixture.sampleUser;
import static ee.tuleva.onboarding.savings.fund.SavingFundPayment.Status.RETURNED;
import static ee.tuleva.onboarding.savings.fund.SavingFundPayment.Status.TO_BE_RETURNED;
import static ee.tuleva.onboarding.savings.fund.SavingFundPaymentFixture.aPayment;
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionRequest.Status.REDEEMED;
import static ee.tuleva.onboarding.swedbank.statement.BankAccountType.*;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import ee.tuleva.onboarding.ledger.SavingsFundLedger;
import ee.tuleva.onboarding.savings.fund.SavingFundPayment;
import ee.tuleva.onboarding.savings.fund.SavingFundPaymentExtractor;
import ee.tuleva.onboarding.savings.fund.SavingFundPaymentRepository;
import ee.tuleva.onboarding.savings.fund.SavingFundPaymentUpsertionService;
import ee.tuleva.onboarding.savings.fund.redemption.RedemptionRequest;
import ee.tuleva.onboarding.savings.fund.redemption.RedemptionRequestRepository;
import ee.tuleva.onboarding.savings.fund.redemption.RedemptionStatusService;
import ee.tuleva.onboarding.swedbank.fetcher.SwedbankAccountConfiguration;
import ee.tuleva.onboarding.swedbank.payment.EndToEndIdConverter;
import ee.tuleva.onboarding.swedbank.statement.BankStatement;
import ee.tuleva.onboarding.swedbank.statement.BankStatement.BankStatementType;
import ee.tuleva.onboarding.swedbank.statement.BankStatementAccount;
import ee.tuleva.onboarding.swedbank.statement.SwedbankBankStatementExtractor;
import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.user.UserService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SwedbankBankStatementMessageProcessorTest {

  private static final String DEPOSIT_ACCOUNT_IBAN = "EE442200221092874625";
  private static final String FUND_INVESTMENT_IBAN = "EE552200221055544433";
  private static final String WITHDRAWAL_ACCOUNT_IBAN = "EE662200221066655544";
  private static final String EXTERNAL_ACCOUNT_IBAN = "EE112233445566778899";

  SwedbankBankStatementExtractor swedbankBankStatementExtractor =
      mock(SwedbankBankStatementExtractor.class);
  SavingFundPaymentExtractor paymentExtractor = mock(SavingFundPaymentExtractor.class);
  SavingFundPaymentUpsertionService paymentService = mock(SavingFundPaymentUpsertionService.class);
  SwedbankAccountConfiguration swedbankAccountConfiguration =
      mock(SwedbankAccountConfiguration.class);
  SavingsFundLedger savingsFundLedger = mock(SavingsFundLedger.class);
  SavingFundPaymentRepository savingFundPaymentRepository = mock(SavingFundPaymentRepository.class);
  UserService userService = mock(UserService.class);
  RedemptionRequestRepository redemptionRequestRepository = mock(RedemptionRequestRepository.class);
  RedemptionStatusService redemptionStatusService = mock(RedemptionStatusService.class);
  EndToEndIdConverter endToEndIdConverter = new EndToEndIdConverter();

  SwedbankBankStatementMessageProcessor processor =
      new SwedbankBankStatementMessageProcessor(
          swedbankBankStatementExtractor,
          paymentExtractor,
          paymentService,
          swedbankAccountConfiguration,
          savingsFundLedger,
          savingFundPaymentRepository,
          userService,
          redemptionRequestRepository,
          redemptionStatusService,
          endToEndIdConverter);

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
    when(userService.getByIdOrThrow(user.getId())).thenReturn(user);

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
  @DisplayName("User-cancelled return throws when user not found")
  void userCancelledReturn_throwsWhenUserNotFound() {
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
    when(userService.getByIdOrThrow(missingUserId)).thenThrow(new NoSuchElementException());

    assertThrows(
        NoSuchElementException.class,
        () -> processor.processMessage("<xml>", SwedbankMessageType.INTRA_DAY_REPORT));
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
    setupMocksForPaymentWithAccount(payment, DEPOSIT_ACCOUNT_IBAN, DEPOSIT_EUR);
  }

  private void setupMocksForPaymentWithAccount(
      SavingFundPayment payment,
      String accountIban,
      ee.tuleva.onboarding.swedbank.statement.BankAccountType accountType) {
    var bankStatement =
        new BankStatement(
            BankStatementType.INTRA_DAY_REPORT,
            new BankStatementAccount(accountIban, "Tuleva Fondid AS", "14118923"),
            List.of(),
            List.of(),
            Instant.now());
    when(swedbankBankStatementExtractor.extractFromIntraDayReport(any())).thenReturn(bankStatement);
    when(swedbankAccountConfiguration.getAccountType(accountIban)).thenReturn(accountType);
    when(paymentExtractor.extractPayments(bankStatement)).thenReturn(List.of(payment));

    doAnswer(
            invocation -> {
              Function<SavingFundPayment, SavingFundPayment.Status> onInsert =
                  invocation.getArgument(1);
              onInsert.apply(payment);
              return null;
            })
        .when(paymentService)
        .upsert(eq(payment), any(), any());

    doAnswer(
            invocation -> {
              Function<SavingFundPayment, SavingFundPayment.Status> onInsert =
                  invocation.getArgument(1);
              onInsert.apply(payment);
              return null;
            })
        .when(paymentService)
        .upsert(eq(payment), any());
  }

  @Test
  @DisplayName(
      "WITHDRAWAL_EUR outgoing payment creates ledger entry and marks RedemptionRequest as PROCESSED")
  void withdrawalOutgoing_createsLedgerEntryAndMarksRedemptionAsProcessed() {
    User user = sampleUser().build();
    var redemptionRequestId = UUID.randomUUID();
    var endToEndId = endToEndIdConverter.toEndToEndId(redemptionRequestId);
    var customerIban = EXTERNAL_ACCOUNT_IBAN;
    var redemptionRequest =
        RedemptionRequest.builder()
            .id(redemptionRequestId)
            .userId(user.getId())
            .customerIban(customerIban)
            .status(REDEEMED)
            .build();
    var outgoingPayment =
        aPayment()
            .amount(new BigDecimal("-500.00"))
            .beneficiaryIban(customerIban)
            .endToEndId(endToEndId)
            .build();
    setupMocksForPaymentWithAccount(outgoingPayment, WITHDRAWAL_ACCOUNT_IBAN, WITHDRAWAL_EUR);
    when(redemptionRequestRepository.findByIdAndStatus(redemptionRequestId, REDEEMED))
        .thenReturn(Optional.of(redemptionRequest));
    when(savingsFundLedger.hasPayoutEntry(redemptionRequestId)).thenReturn(false);
    when(userService.getByIdOrThrow(user.getId())).thenReturn(user);

    processor.processMessage("<xml>", SwedbankMessageType.INTRA_DAY_REPORT);

    verify(savingsFundLedger)
        .recordRedemptionPayout(user, new BigDecimal("500.00"), customerIban, redemptionRequestId);
    verify(redemptionStatusService)
        .changeStatus(redemptionRequestId, RedemptionRequest.Status.PROCESSED);
  }

  @Test
  @DisplayName("WITHDRAWAL_EUR outgoing payment skips ledger entry if already exists")
  void withdrawalOutgoing_skipsLedgerEntryIfAlreadyExists() {
    User user = sampleUser().build();
    var redemptionRequestId = UUID.randomUUID();
    var endToEndId = endToEndIdConverter.toEndToEndId(redemptionRequestId);
    var redemptionRequest =
        RedemptionRequest.builder()
            .id(redemptionRequestId)
            .userId(user.getId())
            .customerIban(EXTERNAL_ACCOUNT_IBAN)
            .status(REDEEMED)
            .build();
    var outgoingPayment =
        aPayment()
            .amount(new BigDecimal("-500.00"))
            .beneficiaryIban(EXTERNAL_ACCOUNT_IBAN)
            .endToEndId(endToEndId)
            .build();
    setupMocksForPaymentWithAccount(outgoingPayment, WITHDRAWAL_ACCOUNT_IBAN, WITHDRAWAL_EUR);
    when(redemptionRequestRepository.findByIdAndStatus(redemptionRequestId, REDEEMED))
        .thenReturn(Optional.of(redemptionRequest));
    when(savingsFundLedger.hasPayoutEntry(redemptionRequestId)).thenReturn(true);

    processor.processMessage("<xml>", SwedbankMessageType.INTRA_DAY_REPORT);

    verify(savingsFundLedger, never()).recordRedemptionPayout(any(), any(), any(), any());
    verify(redemptionStatusService)
        .changeStatus(redemptionRequestId, RedemptionRequest.Status.PROCESSED);
  }

  @Test
  @DisplayName("WITHDRAWAL_EUR outgoing payment throws when user not found")
  void withdrawalOutgoing_throwsWhenUserNotFound() {
    Long missingUserId = 99999L;
    var redemptionRequestId = UUID.randomUUID();
    var endToEndId = endToEndIdConverter.toEndToEndId(redemptionRequestId);
    var redemptionRequest =
        RedemptionRequest.builder()
            .id(redemptionRequestId)
            .userId(missingUserId)
            .customerIban(EXTERNAL_ACCOUNT_IBAN)
            .status(REDEEMED)
            .build();
    var outgoingPayment =
        aPayment()
            .amount(new BigDecimal("-500.00"))
            .beneficiaryIban(EXTERNAL_ACCOUNT_IBAN)
            .endToEndId(endToEndId)
            .build();
    setupMocksForPaymentWithAccount(outgoingPayment, WITHDRAWAL_ACCOUNT_IBAN, WITHDRAWAL_EUR);
    when(redemptionRequestRepository.findByIdAndStatus(redemptionRequestId, REDEEMED))
        .thenReturn(Optional.of(redemptionRequest));
    when(savingsFundLedger.hasPayoutEntry(redemptionRequestId)).thenReturn(false);
    when(userService.getByIdOrThrow(missingUserId)).thenThrow(new NoSuchElementException());

    assertThrows(
        NoSuchElementException.class,
        () -> processor.processMessage("<xml>", SwedbankMessageType.INTRA_DAY_REPORT));
  }

  @Test
  @DisplayName(
      "WITHDRAWAL_EUR outgoing payment with no matching RedemptionRequest does not change status")
  void withdrawalOutgoing_noMatchingRedemption_doesNotChangeStatus() {
    var endToEndId = "12345678123456781234567812345678";
    var outgoingPayment =
        aPayment()
            .amount(new BigDecimal("-500.00"))
            .beneficiaryIban(EXTERNAL_ACCOUNT_IBAN)
            .endToEndId(endToEndId)
            .build();
    setupMocksForPaymentWithAccount(outgoingPayment, WITHDRAWAL_ACCOUNT_IBAN, WITHDRAWAL_EUR);
    when(redemptionRequestRepository.findByIdAndStatus(any(), eq(REDEEMED)))
        .thenReturn(Optional.empty());

    processor.processMessage("<xml>", SwedbankMessageType.INTRA_DAY_REPORT);

    verifyNoInteractions(redemptionStatusService);
  }

  @Test
  @DisplayName("WITHDRAWAL_EUR incoming from FUND_INVESTMENT logs batch transfer")
  void withdrawalIncoming_fromFundInvestment_logsBatchTransfer() {
    var incomingPayment =
        aPayment().amount(new BigDecimal("1000.00")).remitterIban(FUND_INVESTMENT_IBAN).build();
    setupMocksForPaymentWithAccount(incomingPayment, WITHDRAWAL_ACCOUNT_IBAN, WITHDRAWAL_EUR);
    when(swedbankAccountConfiguration.getAccountType(FUND_INVESTMENT_IBAN))
        .thenReturn(FUND_INVESTMENT_EUR);

    processor.processMessage("<xml>", SwedbankMessageType.INTRA_DAY_REPORT);

    verifyNoInteractions(redemptionStatusService);
  }

  @Test
  @DisplayName(
      "FUND_INVESTMENT_EUR outgoing to WITHDRAWAL creates transferFromFundAccount ledger entry")
  void fundInvestmentOutgoing_toWithdrawal_createsLedgerEntry() {
    var outgoingPayment =
        aPayment()
            .amount(new BigDecimal("-1000.00"))
            .beneficiaryIban(WITHDRAWAL_ACCOUNT_IBAN)
            .build();
    setupMocksForPaymentWithAccount(outgoingPayment, FUND_INVESTMENT_IBAN, FUND_INVESTMENT_EUR);
    when(swedbankAccountConfiguration.getAccountType(WITHDRAWAL_ACCOUNT_IBAN))
        .thenReturn(WITHDRAWAL_EUR);

    processor.processMessage("<xml>", SwedbankMessageType.INTRA_DAY_REPORT);

    verify(savingsFundLedger).transferFromFundAccount(new BigDecimal("1000.00"));
    verifyNoInteractions(redemptionStatusService);
    verify(paymentService).upsert(eq(outgoingPayment), any());
  }
}
