package ee.tuleva.onboarding.savings.fund;

import static ee.tuleva.onboarding.auth.UserFixture.sampleUser;
import static ee.tuleva.onboarding.banking.BankAccountType.*;
import static ee.tuleva.onboarding.banking.check.payment.PaymentCheckSeverity.HOLD;
import static ee.tuleva.onboarding.banking.check.payment.PaymentCheckSeverity.WARNING;
import static ee.tuleva.onboarding.banking.check.payment.PaymentCheckType.PAYOUT_WITHOUT_REQUEST;
import static ee.tuleva.onboarding.banking.check.payment.PaymentCheckType.UNMODELLED_DEBIT;
import static ee.tuleva.onboarding.savings.SavingFundPayment.Status.PROCESSED;
import static ee.tuleva.onboarding.savings.SavingFundPayment.Status.RETURNED;
import static ee.tuleva.onboarding.savings.SavingFundPayment.Status.TO_BE_RETURNED;
import static ee.tuleva.onboarding.savings.SavingFundPaymentFixture.aPayment;
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionRequest.Status.REDEEMED;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TKF100;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

import ee.tuleva.onboarding.banking.BankAccount;
import ee.tuleva.onboarding.banking.BankAccountType;
import ee.tuleva.onboarding.banking.BankAccounts;
import ee.tuleva.onboarding.banking.ManagementCompanies;
import ee.tuleva.onboarding.banking.StatementDebit;
import ee.tuleva.onboarding.banking.check.payment.OutgoingPaymentMatcher;
import ee.tuleva.onboarding.banking.check.payment.PaymentCheckService;
import ee.tuleva.onboarding.banking.payment.EndToEndIdConverter;
import ee.tuleva.onboarding.banking.payment.OutgoingPaymentLookup;
import ee.tuleva.onboarding.banking.seb.SebAccountConfiguration;
import ee.tuleva.onboarding.banking.statement.BankStatement;
import ee.tuleva.onboarding.banking.statement.BankStatement.BankStatementType;
import ee.tuleva.onboarding.banking.statement.BankStatementAccount;
import ee.tuleva.onboarding.banking.statement.StatementPeriod;
import ee.tuleva.onboarding.ledger.FundBankLedger;
import ee.tuleva.onboarding.ledger.InternalTransferLedger;
import ee.tuleva.onboarding.ledger.LedgerParty.PartyType;
import ee.tuleva.onboarding.ledger.PartyRef;
import ee.tuleva.onboarding.ledger.PayoutAccountReceiptLedger;
import ee.tuleva.onboarding.ledger.SavingsFundLedger;
import ee.tuleva.onboarding.ledger.SystemAccount;
import ee.tuleva.onboarding.party.PartyId;
import ee.tuleva.onboarding.savings.SavingFundPayment;
import ee.tuleva.onboarding.savings.fund.notification.OwnAccountTransferRecordedEvent;
import ee.tuleva.onboarding.savings.fund.redemption.RedemptionPayoutRecorder;
import ee.tuleva.onboarding.savings.fund.redemption.RedemptionRequest;
import ee.tuleva.onboarding.savings.fund.redemption.RedemptionRequestRepository;
import ee.tuleva.onboarding.savings.fund.redemption.RedemptionStatusService;
import ee.tuleva.onboarding.user.User;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class SavingsFundStatementProcessorTest {

  private static final StatementPeriod STATEMENT_PERIOD =
      new StatementPeriod(LocalDate.of(2026, 1, 12), LocalDate.of(2026, 1, 12));

  private BankAccount statementAccount;
  private SavingFundPayment.Status insertedStatus;

  private static final String DEPOSIT_ACCOUNT_IBAN = "EE442200221092874625";
  private static final String FUND_INVESTMENT_IBAN = "EE552200221055544433";
  private static final String WITHDRAWAL_ACCOUNT_IBAN = "EE662200221066655544";
  private static final String EXTERNAL_ACCOUNT_IBAN = "EE112233445566778899";

  SavingFundPaymentExtractor paymentExtractor = mock(SavingFundPaymentExtractor.class);
  SavingFundPaymentUpsertionService paymentService = mock(SavingFundPaymentUpsertionService.class);
  SebAccountConfiguration sebAccountConfiguration = mock(SebAccountConfiguration.class);
  ManagementCompanies managementCompanies = new ManagementCompanies(sebAccountConfiguration);
  BankAccounts bankAccounts = mock(BankAccounts.class);
  SavingsFundLedger savingsFundLedger = mock(SavingsFundLedger.class);
  InternalTransferLedger internalTransferLedger = mock(InternalTransferLedger.class);
  ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
  OwnAccountTransferRecorder ownAccountTransferRecorder =
      new OwnAccountTransferRecorder(bankAccounts, internalTransferLedger, eventPublisher);
  FundBankLedger fundBankLedger = mock(FundBankLedger.class);
  RedemptionRequestRepository redemptionRequestRepository = mock(RedemptionRequestRepository.class);
  RedemptionStatusService redemptionStatusService = mock(RedemptionStatusService.class);
  EndToEndIdConverter endToEndIdConverter = new EndToEndIdConverter();
  PaymentCheckService paymentCheckService = mock(PaymentCheckService.class);
  OutgoingPaymentMatcher outgoingPaymentMatcher = mock(OutgoingPaymentMatcher.class);
  RedemptionPayoutRecorder redemptionPayoutRecorder =
      new RedemptionPayoutRecorder(
          savingsFundLedger,
          redemptionRequestRepository,
          redemptionStatusService,
          endToEndIdConverter,
          paymentCheckService);
  SavingFundPaymentRepository savingFundPaymentRepository = mock(SavingFundPaymentRepository.class);
  OutgoingPaymentLookup outgoingPaymentLookup = mock(OutgoingPaymentLookup.class);
  PayoutAccountReceiptLedger payoutAccountReceiptLedger = mock(PayoutAccountReceiptLedger.class);

  SavingsFundStatementProcessor processor =
      new SavingsFundStatementProcessor(
          paymentExtractor,
          paymentService,
          managementCompanies,
          bankAccounts,
          savingsFundLedger,
          ownAccountTransferRecorder,
          fundBankLedger,
          paymentCheckService,
          outgoingPaymentMatcher,
          redemptionPayoutRecorder,
          savingFundPaymentRepository,
          outgoingPaymentLookup,
          payoutAccountReceiptLedger);

  @Test
  void outgoingToFundAccount_createsLedgerTransferEntryWithBookingDate() {
    var receivedBefore = Instant.parse("2025-10-01T20:59:59.999999Z");
    var outgoingPayment =
        aPayment()
            .amount(new BigDecimal("-100.00"))
            .beneficiaryIban(FUND_INVESTMENT_IBAN)
            .receivedBefore(receivedBefore)
            .build();
    var bankStatement = setupMocksForPayment(outgoingPayment);
    when(bankAccounts.find(FUND_INVESTMENT_IBAN))
        .thenReturn(
            Optional.of(
                new BankAccount(FUND_INVESTMENT_IBAN, FUND_INVESTMENT_EUR, TKF100, "gw-test")));

    processor.process(bankStatement, statementAccount);

    verify(savingsFundLedger)
        .transferToFundAccount(
            new BigDecimal("100.00"), outgoingPayment.getId(), LocalDate.of(2025, 10, 1));
  }

  @Test
  void fundInvestmentOutgoingToDepositAccount_recordsInternalTransfer() {
    var receivedBefore = Instant.parse("2026-09-01T06:59:59.999999Z");
    var correctionTransfer =
        aPayment()
            .amount(new BigDecimal("-4272.98"))
            .beneficiaryIban(DEPOSIT_ACCOUNT_IBAN)
            .description("Management fee kickback 02/2026")
            .receivedBefore(receivedBefore)
            .build();
    var bankStatement =
        setupMocksForPaymentWithAccount(
            correctionTransfer, FUND_INVESTMENT_IBAN, FUND_INVESTMENT_EUR);
    when(bankAccounts.find(DEPOSIT_ACCOUNT_IBAN))
        .thenReturn(
            Optional.of(new BankAccount(DEPOSIT_ACCOUNT_IBAN, DEPOSIT_EUR, TKF100, "gw-test")));

    processor.process(bankStatement, statementAccount);

    verify(internalTransferLedger)
        .recordInternalTransfer(
            SystemAccount.FUND_INVESTMENT_CASH_CLEARING,
            SystemAccount.INCOMING_PAYMENTS_CLEARING,
            new BigDecimal("4272.98"),
            correctionTransfer.getId(),
            LocalDate.of(2026, 9, 1),
            "Management fee kickback 02/2026");
    verify(eventPublisher)
        .publishEvent(
            new OwnAccountTransferRecordedEvent(
                FUND_INVESTMENT_EUR,
                DEPOSIT_EUR,
                new BigDecimal("4272.98"),
                correctionTransfer.getId()));
  }

  @Test
  void fundInvestmentOutgoingToDepositAccount_withFeeLikeDescription_isNotBookedAsManagementFee() {
    var receivedBefore = Instant.parse("2026-09-01T06:59:59.999999Z");
    var transfer =
        aPayment()
            .amount(new BigDecimal("-100.00"))
            .beneficiaryIban(DEPOSIT_ACCOUNT_IBAN)
            .beneficiaryName("Tuleva Fondid AS")
            .description("Valitsemistasu tagastus")
            .receivedBefore(receivedBefore)
            .build();
    var bankStatement =
        setupMocksForPaymentWithAccount(transfer, FUND_INVESTMENT_IBAN, FUND_INVESTMENT_EUR);
    when(managementCompanies.isManagementCompany("Tuleva Fondid AS")).thenReturn(true);
    when(bankAccounts.find(DEPOSIT_ACCOUNT_IBAN))
        .thenReturn(
            Optional.of(new BankAccount(DEPOSIT_ACCOUNT_IBAN, DEPOSIT_EUR, TKF100, "gw-test")));

    processor.process(bankStatement, statementAccount);

    verify(internalTransferLedger)
        .recordInternalTransfer(
            SystemAccount.FUND_INVESTMENT_CASH_CLEARING,
            SystemAccount.INCOMING_PAYMENTS_CLEARING,
            new BigDecimal("100.00"),
            transfer.getId(),
            LocalDate.of(2026, 9, 1),
            "Valitsemistasu tagastus");
    verifyNoInteractions(fundBankLedger);
  }

  @Test
  void depositOutgoingToWithdrawalAccount_recordsInternalTransferInsteadOfDeferredReturn() {
    var receivedBefore = Instant.parse("2026-09-01T06:59:59.999999Z");
    var transfer =
        aPayment()
            .amount(new BigDecimal("-50.00"))
            .beneficiaryIban(WITHDRAWAL_ACCOUNT_IBAN)
            .description("internal move")
            .receivedBefore(receivedBefore)
            .build();
    var bankStatement = setupMocksForPayment(transfer);
    when(bankAccounts.find(WITHDRAWAL_ACCOUNT_IBAN))
        .thenReturn(
            Optional.of(
                new BankAccount(WITHDRAWAL_ACCOUNT_IBAN, WITHDRAWAL_EUR, TKF100, "gw-test")));

    processor.process(bankStatement, statementAccount);

    verify(internalTransferLedger)
        .recordInternalTransfer(
            SystemAccount.INCOMING_PAYMENTS_CLEARING,
            SystemAccount.PAYOUTS_CASH_CLEARING,
            new BigDecimal("50.00"),
            transfer.getId(),
            LocalDate.of(2026, 9, 1),
            "internal move");
  }

  @Test
  void outgoingReturn_defersMatchingToPostProcessingPass() {
    var returnPayment =
        aPayment()
            .amount(new BigDecimal("-50.00"))
            .beneficiaryIban(EXTERNAL_ACCOUNT_IBAN)
            .endToEndId("nonexistent12345678901234567890")
            .build();
    var bankStatement = setupMocksForPayment(returnPayment);
    when(bankAccounts.find(EXTERNAL_ACCOUNT_IBAN)).thenReturn(Optional.empty());

    processor.process(bankStatement, statementAccount);

    verify(savingsFundLedger, never()).recordPaymentCancelled(any(), any(), any());
    verify(savingsFundLedger, never()).bounceBackUnattributedPayment(any(), any());
  }

  @Test
  void outgoingPaymentToPensionFundAccount_isNotBookedAsInternalFundTransfer() {
    var pensionIban = "EE001234567890123475";
    var outgoingPayment =
        aPayment()
            .amount(new BigDecimal("-100000.00"))
            .beneficiaryIban(pensionIban)
            .endToEndId("nonexistent12345678901234567890")
            .build();
    var bankStatement = setupMocksForPayment(outgoingPayment);
    when(bankAccounts.find(pensionIban))
        .thenReturn(
            Optional.of(
                new BankAccount(
                    pensionIban,
                    FUND_INVESTMENT_EUR,
                    ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75,
                    "gw-test")));

    processor.process(bankStatement, statementAccount);

    verify(savingsFundLedger, never()).transferToFundAccount(any(), any(), any());
    verify(savingsFundLedger, never()).transferToFundAccount(any(), any());
  }

  @Test
  void incomingPayment_doesNotCreateLedgerEntry() {
    var incomingPayment = aPayment().amount(new BigDecimal("200.00")).build();
    var bankStatement = setupMocksForPayment(incomingPayment);

    processor.process(bankStatement, statementAccount);

    verify(savingsFundLedger, never()).transferToFundAccount(any(), any());
    verify(savingsFundLedger, never()).recordPaymentCancelled(any(), any(), any());
    verify(savingsFundLedger, never()).bounceBackUnattributedPayment(any(), any());
  }

  private BankStatement setupMocksForPayment(SavingFundPayment payment) {
    return setupMocksForPaymentWithAccount(payment, DEPOSIT_ACCOUNT_IBAN, DEPOSIT_EUR);
  }

  private BankStatement setupMocksForPaymentWithAccount(
      SavingFundPayment payment, String accountIban, BankAccountType accountType) {
    var bankStatement =
        new BankStatement(
            BankStatementType.INTRA_DAY_REPORT,
            new BankStatementAccount(accountIban, "Tuleva Fondid AS", "14118923"),
            List.of(),
            List.of(),
            STATEMENT_PERIOD);
    statementAccount = new BankAccount(accountIban, accountType, TKF100, "gw-test");
    when(paymentExtractor.extractPayments(bankStatement)).thenReturn(List.of(payment));

    doAnswer(
            invocation -> {
              Function<SavingFundPayment, SavingFundPayment.Status> onInsert =
                  invocation.getArgument(1);
              insertedStatus = onInsert.apply(payment);
              return null;
            })
        .when(paymentService)
        .upsert(eq(payment), any(), any());

    doAnswer(
            invocation -> {
              Function<SavingFundPayment, SavingFundPayment.Status> onInsert =
                  invocation.getArgument(1);
              insertedStatus = onInsert.apply(payment);
              return null;
            })
        .when(paymentService)
        .upsert(eq(payment), any());

    return bankStatement;
  }

  @Test
  void withdrawalOutgoing_createsLedgerEntryAndMarksRedemptionAsProcessed() {
    User user = sampleUser().build();
    var redemptionRequestId = UUID.randomUUID();
    var endToEndId = endToEndIdConverter.toEndToEndId(redemptionRequestId);
    var customerIban = EXTERNAL_ACCOUNT_IBAN;
    var receivedBefore = Instant.parse("2025-10-01T20:59:59.999999Z");
    var redemptionRequest =
        RedemptionRequest.builder()
            .id(redemptionRequestId)
            .userId(user.getId())
            .partyType(PartyId.Type.PERSON)
            .partyCode(user.getPersonalCode())
            .customerIban(customerIban)
            .status(REDEEMED)
            .build();
    var outgoingPayment =
        aPayment()
            .amount(new BigDecimal("-500.00"))
            .beneficiaryIban(customerIban)
            .endToEndId(endToEndId)
            .receivedBefore(receivedBefore)
            .build();
    var bankStatement =
        setupMocksForPaymentWithAccount(outgoingPayment, WITHDRAWAL_ACCOUNT_IBAN, WITHDRAWAL_EUR);
    when(redemptionRequestRepository.findByIdAndStatus(redemptionRequestId, REDEEMED))
        .thenReturn(Optional.of(redemptionRequest));
    when(savingsFundLedger.hasPayoutEntry(redemptionRequestId)).thenReturn(false);

    processor.process(bankStatement, statementAccount);

    var expectedParty = new PartyRef(PartyType.PERSON, user.getPersonalCode());
    verify(savingsFundLedger)
        .recordRedemptionPayout(
            expectedParty,
            new BigDecimal("500.00"),
            customerIban,
            redemptionRequestId,
            LocalDate.of(2025, 10, 1));
    verify(redemptionStatusService)
        .changeStatus(redemptionRequestId, RedemptionRequest.Status.PROCESSED);
  }

  @Test
  void withdrawalOutgoing_booksPayoutToTheRedeemingCompanyNotToItsRepresentative() {
    User boardMember = sampleUser().build();
    var companyRegistryCode = "12345678";
    var redemptionRequestId = UUID.randomUUID();
    var endToEndId = endToEndIdConverter.toEndToEndId(redemptionRequestId);
    var customerIban = EXTERNAL_ACCOUNT_IBAN;
    var redemptionRequest =
        RedemptionRequest.builder()
            .id(redemptionRequestId)
            .userId(boardMember.getId())
            .partyType(PartyId.Type.LEGAL_ENTITY)
            .partyCode(companyRegistryCode)
            .customerIban(customerIban)
            .status(REDEEMED)
            .build();
    var outgoingPayment =
        aPayment()
            .amount(new BigDecimal("-2991.94"))
            .beneficiaryIban(customerIban)
            .endToEndId(endToEndId)
            .receivedBefore(Instant.parse("2025-10-01T20:59:59.999999Z"))
            .build();
    var bankStatement =
        setupMocksForPaymentWithAccount(outgoingPayment, WITHDRAWAL_ACCOUNT_IBAN, WITHDRAWAL_EUR);
    when(redemptionRequestRepository.findByIdAndStatus(redemptionRequestId, REDEEMED))
        .thenReturn(Optional.of(redemptionRequest));
    when(savingsFundLedger.hasPayoutEntry(redemptionRequestId)).thenReturn(false);

    processor.process(bankStatement, statementAccount);

    verify(savingsFundLedger)
        .recordRedemptionPayout(
            new PartyRef(PartyType.LEGAL_ENTITY, companyRegistryCode),
            new BigDecimal("2991.94"),
            customerIban,
            redemptionRequestId,
            LocalDate.of(2025, 10, 1));
  }

  @Test
  void withdrawalOutgoing_booksPayoutToTheChildNotToTheParentWhoRequestedIt() {
    User parent = sampleUser().build();
    var childPersonalCode = "51501010000";
    var redemptionRequestId = UUID.randomUUID();
    var endToEndId = endToEndIdConverter.toEndToEndId(redemptionRequestId);
    var redemptionRequest =
        RedemptionRequest.builder()
            .id(redemptionRequestId)
            .userId(parent.getId())
            .partyType(PartyId.Type.PERSON)
            .partyCode(childPersonalCode)
            .customerIban(EXTERNAL_ACCOUNT_IBAN)
            .status(REDEEMED)
            .build();
    var outgoingPayment =
        aPayment()
            .amount(new BigDecimal("-49.93"))
            .beneficiaryIban(EXTERNAL_ACCOUNT_IBAN)
            .endToEndId(endToEndId)
            .receivedBefore(Instant.parse("2025-10-01T20:59:59.999999Z"))
            .build();
    var bankStatement =
        setupMocksForPaymentWithAccount(outgoingPayment, WITHDRAWAL_ACCOUNT_IBAN, WITHDRAWAL_EUR);
    when(redemptionRequestRepository.findByIdAndStatus(redemptionRequestId, REDEEMED))
        .thenReturn(Optional.of(redemptionRequest));
    when(savingsFundLedger.hasPayoutEntry(redemptionRequestId)).thenReturn(false);

    processor.process(bankStatement, statementAccount);

    verify(savingsFundLedger)
        .recordRedemptionPayout(
            new PartyRef(PartyType.PERSON, childPersonalCode),
            new BigDecimal("49.93"),
            EXTERNAL_ACCOUNT_IBAN,
            redemptionRequestId,
            LocalDate.of(2025, 10, 1));
  }

  @Test
  void withdrawalOutgoing_skipsLedgerEntryIfAlreadyExists() {
    User user = sampleUser().build();
    var redemptionRequestId = UUID.randomUUID();
    var endToEndId = endToEndIdConverter.toEndToEndId(redemptionRequestId);
    var redemptionRequest =
        RedemptionRequest.builder()
            .id(redemptionRequestId)
            .userId(user.getId())
            .partyType(PartyId.Type.PERSON)
            .partyCode(user.getPersonalCode())
            .customerIban(EXTERNAL_ACCOUNT_IBAN)
            .status(REDEEMED)
            .build();
    var outgoingPayment =
        aPayment()
            .amount(new BigDecimal("-500.00"))
            .beneficiaryIban(EXTERNAL_ACCOUNT_IBAN)
            .endToEndId(endToEndId)
            .build();
    var bankStatement =
        setupMocksForPaymentWithAccount(outgoingPayment, WITHDRAWAL_ACCOUNT_IBAN, WITHDRAWAL_EUR);
    when(redemptionRequestRepository.findByIdAndStatus(redemptionRequestId, REDEEMED))
        .thenReturn(Optional.of(redemptionRequest));
    when(savingsFundLedger.hasPayoutEntry(redemptionRequestId)).thenReturn(true);

    processor.process(bankStatement, statementAccount);

    verify(savingsFundLedger, never()).recordRedemptionPayout(any(), any(), any(), any());
    verify(redemptionStatusService)
        .changeStatus(redemptionRequestId, RedemptionRequest.Status.PROCESSED);
  }

  @Test
  void withdrawalOutgoing_noMatchingRedemption_doesNotChangeStatus() {
    var endToEndId = "12345678123456781234567812345678";
    var outgoingPayment =
        aPayment()
            .amount(new BigDecimal("-500.00"))
            .beneficiaryIban(EXTERNAL_ACCOUNT_IBAN)
            .endToEndId(endToEndId)
            .build();
    var bankStatement =
        setupMocksForPaymentWithAccount(outgoingPayment, WITHDRAWAL_ACCOUNT_IBAN, WITHDRAWAL_EUR);
    when(redemptionRequestRepository.findByIdAndStatus(any(), eq(REDEEMED)))
        .thenReturn(Optional.empty());

    processor.process(bankStatement, statementAccount);

    verifyNoInteractions(redemptionStatusService);
  }

  @Test
  void withdrawalIncoming_fromFundInvestment_logsBatchTransfer() {
    var incomingPayment =
        aPayment().amount(new BigDecimal("1000.00")).remitterIban(FUND_INVESTMENT_IBAN).build();
    var bankStatement =
        setupMocksForPaymentWithAccount(incomingPayment, WITHDRAWAL_ACCOUNT_IBAN, WITHDRAWAL_EUR);
    when(bankAccounts.find(FUND_INVESTMENT_IBAN))
        .thenReturn(
            Optional.of(
                new BankAccount(FUND_INVESTMENT_IBAN, FUND_INVESTMENT_EUR, TKF100, "gw-test")));

    processor.process(bankStatement, statementAccount);

    verifyNoInteractions(redemptionStatusService);
  }

  @Test
  void fundInvestmentOutgoing_toWithdrawal_createsLedgerEntryWithBookingDate() {
    var receivedBefore = Instant.parse("2025-10-01T20:59:59.999999Z");
    var outgoingPayment =
        aPayment()
            .amount(new BigDecimal("-1000.00"))
            .beneficiaryIban(WITHDRAWAL_ACCOUNT_IBAN)
            .receivedBefore(receivedBefore)
            .build();
    var bankStatement =
        setupMocksForPaymentWithAccount(outgoingPayment, FUND_INVESTMENT_IBAN, FUND_INVESTMENT_EUR);
    when(bankAccounts.find(WITHDRAWAL_ACCOUNT_IBAN))
        .thenReturn(
            Optional.of(
                new BankAccount(WITHDRAWAL_ACCOUNT_IBAN, WITHDRAWAL_EUR, TKF100, "gw-test")));

    processor.process(bankStatement, statementAccount);

    verify(savingsFundLedger)
        .transferFromFundAccount(
            new BigDecimal("1000.00"), outgoingPayment.getId(), LocalDate.of(2025, 10, 1));
    verifyNoInteractions(redemptionStatusService);
    verify(paymentService).upsert(eq(outgoingPayment), any());
  }

  @Test
  void incomingInternalTransfer_depositToFundInvestment_isSkipped() {
    var incomingPayment =
        aPayment().amount(new BigDecimal("1000.00")).remitterIban(DEPOSIT_ACCOUNT_IBAN).build();
    var bankStatement =
        setupMocksForPaymentWithAccount(incomingPayment, FUND_INVESTMENT_IBAN, FUND_INVESTMENT_EUR);
    when(bankAccounts.find(DEPOSIT_ACCOUNT_IBAN))
        .thenReturn(
            Optional.of(new BankAccount(DEPOSIT_ACCOUNT_IBAN, DEPOSIT_EUR, TKF100, "gw-test")));

    processor.process(bankStatement, statementAccount);

    verify(paymentService, never()).upsert(any(), any());
    verify(paymentService, never()).upsert(any(), any(), any());
  }

  @Test
  void incomingInternalTransfer_fundInvestmentToWithdrawal_isSkipped() {
    var incomingPayment =
        aPayment().amount(new BigDecimal("1000.00")).remitterIban(FUND_INVESTMENT_IBAN).build();
    var bankStatement =
        setupMocksForPaymentWithAccount(incomingPayment, WITHDRAWAL_ACCOUNT_IBAN, WITHDRAWAL_EUR);
    when(bankAccounts.find(FUND_INVESTMENT_IBAN))
        .thenReturn(
            Optional.of(
                new BankAccount(FUND_INVESTMENT_IBAN, FUND_INVESTMENT_EUR, TKF100, "gw-test")));

    processor.process(bankStatement, statementAccount);

    verify(paymentService, never()).upsert(any(), any());
    verify(paymentService, never()).upsert(any(), any(), any());
  }

  @Test
  void incomingInternalTransfer_fundInvestmentToDeposit_isSkipped() {
    var incomingPayment =
        aPayment().amount(new BigDecimal("1000.00")).remitterIban(FUND_INVESTMENT_IBAN).build();
    var bankStatement =
        setupMocksForPaymentWithAccount(incomingPayment, DEPOSIT_ACCOUNT_IBAN, DEPOSIT_EUR);
    when(bankAccounts.find(FUND_INVESTMENT_IBAN))
        .thenReturn(
            Optional.of(
                new BankAccount(FUND_INVESTMENT_IBAN, FUND_INVESTMENT_EUR, TKF100, "gw-test")));

    processor.process(bankStatement, statementAccount);

    verify(paymentService, never()).upsert(any(), any());
    verify(paymentService, never()).upsert(any(), any(), any());
  }

  @Test
  void incomingExternalPayment_isProcessedNormally() {
    var incomingPayment =
        aPayment().amount(new BigDecimal("200.00")).remitterIban(EXTERNAL_ACCOUNT_IBAN).build();
    var bankStatement = setupMocksForPayment(incomingPayment);
    when(bankAccounts.find(EXTERNAL_ACCOUNT_IBAN)).thenReturn(Optional.empty());

    processor.process(bankStatement, statementAccount);

    verify(paymentService).upsert(eq(incomingPayment), any(), any());
  }

  @Test
  void fundInvestmentOutgoing_managementFee_createsLedgerEntryWithBookingDate() {
    var managementCompanyName = "Tuleva Fondid AS";
    var description = "Valitsemistasu 02.-28.02.26";
    var receivedBefore = Instant.parse("2025-10-01T20:59:59.999999Z");
    var outgoingPayment =
        aPayment()
            .amount(new BigDecimal("-742.34"))
            .beneficiaryIban(EXTERNAL_ACCOUNT_IBAN)
            .beneficiaryName(managementCompanyName)
            .description(description)
            .receivedBefore(receivedBefore)
            .build();
    var bankStatement =
        setupMocksForPaymentWithAccount(outgoingPayment, FUND_INVESTMENT_IBAN, FUND_INVESTMENT_EUR);
    when(sebAccountConfiguration.isManagementCompany(managementCompanyName)).thenReturn(true);

    processor.process(bankStatement, statementAccount);

    verify(fundBankLedger)
        .recordManagementFeePayment(
            TKF100,
            new BigDecimal("742.34"),
            outgoingPayment.getId(),
            description,
            LocalDate.of(2025, 10, 1));
  }

  @Test
  void fundInvestmentOutgoing_unknownPayment_doesNotCreateLedgerEntry() {
    var outgoingPayment =
        aPayment()
            .amount(new BigDecimal("-500.00"))
            .beneficiaryIban(EXTERNAL_ACCOUNT_IBAN)
            .beneficiaryName("Unknown Company")
            .description("Some unknown payment")
            .build();
    var bankStatement =
        setupMocksForPaymentWithAccount(outgoingPayment, FUND_INVESTMENT_IBAN, FUND_INVESTMENT_EUR);
    when(bankAccounts.find(EXTERNAL_ACCOUNT_IBAN)).thenReturn(Optional.empty());
    when(sebAccountConfiguration.isManagementCompany("Unknown Company")).thenReturn(false);

    processor.process(bankStatement, statementAccount);

    verify(fundBankLedger, never()).recordManagementFeePayment(any(), any(), any(), any(), any());
    verify(savingsFundLedger, never()).transferFromFundAccount(any(), any());
  }

  @Test
  void outgoingInternalTransfer_isProcessedNormally() {
    var receivedBefore = Instant.parse("2025-10-01T20:59:59.999999Z");
    var outgoingPayment =
        aPayment()
            .amount(new BigDecimal("-100.00"))
            .beneficiaryIban(FUND_INVESTMENT_IBAN)
            .remitterIban(DEPOSIT_ACCOUNT_IBAN)
            .receivedBefore(receivedBefore)
            .build();
    var bankStatement = setupMocksForPayment(outgoingPayment);
    when(bankAccounts.find(FUND_INVESTMENT_IBAN))
        .thenReturn(
            Optional.of(
                new BankAccount(FUND_INVESTMENT_IBAN, FUND_INVESTMENT_EUR, TKF100, "gw-test")));

    processor.process(bankStatement, statementAccount);

    verify(paymentService).upsert(eq(outgoingPayment), any(), any());
    verify(savingsFundLedger)
        .transferToFundAccount(
            new BigDecimal("100.00"), outgoingPayment.getId(), LocalDate.of(2025, 10, 1));
  }

  @Test
  void aDebitIsHandedToTheMatcherKeyedByItsStatementEntry() {
    var outgoingPayment =
        aPayment()
            .id(null)
            .externalId("seb-entry-1")
            .endToEndId("e2e-1")
            .amount(new BigDecimal("-100.00"))
            .beneficiaryIban(EXTERNAL_ACCOUNT_IBAN)
            .build();
    var bankStatement = setupMocksForPayment(outgoingPayment);
    when(bankAccounts.find(EXTERNAL_ACCOUNT_IBAN)).thenReturn(Optional.empty());

    processor.process(bankStatement, statementAccount);

    verify(outgoingPaymentMatcher)
        .match(
            new StatementDebit(
                "seb-entry-1",
                new BigDecimal("-100.00"),
                EXTERNAL_ACCOUNT_IBAN,
                "Test Beneficiary",
                "Test payment",
                "e2e-1"));
  }

  @Test
  void incomingPaymentWithoutRemitterIban_isProcessedNormally() {
    var incomingPayment = aPayment().amount(new BigDecimal("200.00")).remitterIban(null).build();
    var bankStatement = setupMocksForPayment(incomingPayment);

    processor.process(bankStatement, statementAccount);

    verify(paymentService).upsert(eq(incomingPayment), any(), any());
  }

  @Test
  void depositOutgoingWithoutBeneficiaryIban_isNotBookedAsTransferToTheFund() {
    var outgoingPayment =
        aPayment()
            .amount(new BigDecimal("-100.00"))
            .beneficiaryIban(null)
            .receivedBefore(Instant.parse("2025-10-01T20:59:59.999999Z"))
            .build();
    var bankStatement = setupMocksForPayment(outgoingPayment);

    processor.process(bankStatement, statementAccount);

    verify(paymentService).upsert(eq(outgoingPayment), any(), any());
    verify(savingsFundLedger, never()).transferToFundAccount(any(), any(), any());
  }

  @Test
  void fundInvestmentOutgoingWithoutBeneficiaryName_isNotBookedAsManagementFee() {
    var outgoingPayment =
        aPayment()
            .amount(new BigDecimal("-742.34"))
            .beneficiaryIban(EXTERNAL_ACCOUNT_IBAN)
            .beneficiaryName(null)
            .description("Valitsemistasu 02.-28.02.26")
            .receivedBefore(Instant.parse("2025-10-01T20:59:59.999999Z"))
            .build();
    var bankStatement =
        setupMocksForPaymentWithAccount(outgoingPayment, FUND_INVESTMENT_IBAN, FUND_INVESTMENT_EUR);

    processor.process(bankStatement, statementAccount);

    verify(fundBankLedger, never()).recordManagementFeePayment(any(), any(), any(), any(), any());
  }

  @Test
  void withdrawalIncoming_fromAnOutsidePayer_isParkedInTheLedgerAndReturnedToThem() {
    var incomingPayment =
        aPayment()
            .amount(new BigDecimal("1006.87"))
            .remitterIban(EXTERNAL_ACCOUNT_IBAN)
            .endToEndId("59")
            .receivedBefore(Instant.parse("2025-10-01T20:59:59.999999Z"))
            .build();
    var bankStatement =
        setupMocksForPaymentWithAccount(incomingPayment, WITHDRAWAL_ACCOUNT_IBAN, WITHDRAWAL_EUR);
    given(bankAccounts.find(EXTERNAL_ACCOUNT_IBAN)).willReturn(Optional.empty());
    given(outgoingPaymentLookup.isOurs("59")).willReturn(false);

    processor.process(bankStatement, statementAccount);

    assertThat(insertedStatus).isEqualTo(TO_BE_RETURNED);
    verify(savingFundPaymentRepository)
        .addReturnReason(incomingPayment.getId(), "väljamaksete kontole ei saa sissemakseid teha");
    verify(payoutAccountReceiptLedger)
        .recordReceipt(
            new BigDecimal("1006.87"), incomingPayment.getId(), LocalDate.of(2025, 10, 1));
    verifyNoInteractions(paymentCheckService);
  }

  @Test
  void withdrawalIncoming_thatIsOurOwnPaymentComingBack_isNotReturnedAgainButRaisesAWarning() {
    var payoutEndToEndId = endToEndIdConverter.toEndToEndId(UUID.randomUUID());
    var bouncedPayout =
        aPayment()
            .amount(new BigDecimal("500.00"))
            .remitterIban(EXTERNAL_ACCOUNT_IBAN)
            .endToEndId(payoutEndToEndId)
            .build();
    var bankStatement =
        setupMocksForPaymentWithAccount(bouncedPayout, WITHDRAWAL_ACCOUNT_IBAN, WITHDRAWAL_EUR);
    given(bankAccounts.find(EXTERNAL_ACCOUNT_IBAN)).willReturn(Optional.empty());
    given(outgoingPaymentLookup.isOurs(payoutEndToEndId)).willReturn(true);

    processor.process(bankStatement, statementAccount);

    assertThat(insertedStatus).isEqualTo(PROCESSED);
    verify(savingFundPaymentRepository, never()).addReturnReason(any(), any());
    verify(payoutAccountReceiptLedger, never()).recordReceipt(any(), any(), any());
    verify(paymentCheckService)
        .record(
            UNMODELLED_DEBIT,
            WARNING,
            String.valueOf(bouncedPayout.getId()),
            "a payment we sent from the payout account came back to it");
  }

  @Test
  void
      withdrawalOutgoing_returnOfAPaymentReceivedOnThePayoutAccount_isBookedAndFinishesTheReturn() {
    var originalPayment =
        aPayment()
            .id(UUID.randomUUID())
            .amount(new BigDecimal("1006.87"))
            .beneficiaryIban(WITHDRAWAL_ACCOUNT_IBAN)
            .status(TO_BE_RETURNED)
            .build();
    var returnEndToEndId = endToEndIdConverter.toEndToEndId(originalPayment.getId());
    var returnPayment =
        aPayment()
            .amount(new BigDecimal("-1006.87"))
            .beneficiaryIban(EXTERNAL_ACCOUNT_IBAN)
            .endToEndId(returnEndToEndId)
            .receivedBefore(Instant.parse("2026-11-26T09:00:00Z"))
            .build();
    var bankStatement =
        setupMocksForPaymentWithAccount(returnPayment, WITHDRAWAL_ACCOUNT_IBAN, WITHDRAWAL_EUR);
    given(savingFundPaymentRepository.findOriginalPaymentForReturn(returnEndToEndId))
        .willReturn(Optional.of(originalPayment));
    given(bankAccounts.find(WITHDRAWAL_ACCOUNT_IBAN))
        .willReturn(
            Optional.of(
                new BankAccount(WITHDRAWAL_ACCOUNT_IBAN, WITHDRAWAL_EUR, TKF100, "gw-test")));

    processor.process(bankStatement, statementAccount);

    assertThat(insertedStatus).isEqualTo(PROCESSED);
    verify(payoutAccountReceiptLedger)
        .recordReturn(
            new BigDecimal("1006.87"), originalPayment.getId(), LocalDate.of(2026, 11, 26));
    verify(savingFundPaymentRepository).changeStatus(originalPayment.getId(), RETURNED);
    verify(paymentCheckService, never()).record(eq(PAYOUT_WITHOUT_REQUEST), eq(HOLD), any(), any());
    verify(savingsFundLedger, never()).recordRedemptionPayout(any(), any(), any(), any(), any());
    verifyNoInteractions(redemptionRequestRepository, redemptionStatusService);
  }

  @Test
  void withdrawalOutgoing_matchingAPaymentThatLandedOnAnotherAccount_isNotBookedAsItsReturn() {
    var depositPayment =
        aPayment()
            .id(UUID.randomUUID())
            .amount(new BigDecimal("75.00"))
            .beneficiaryIban(DEPOSIT_ACCOUNT_IBAN)
            .status(TO_BE_RETURNED)
            .build();
    var endToEndId = endToEndIdConverter.toEndToEndId(depositPayment.getId());
    var debit =
        aPayment()
            .amount(new BigDecimal("-75.00"))
            .beneficiaryIban(EXTERNAL_ACCOUNT_IBAN)
            .endToEndId(endToEndId)
            .receivedBefore(Instant.parse("2026-11-26T09:00:00Z"))
            .build();
    var bankStatement =
        setupMocksForPaymentWithAccount(debit, WITHDRAWAL_ACCOUNT_IBAN, WITHDRAWAL_EUR);
    given(savingFundPaymentRepository.findOriginalPaymentForReturn(endToEndId))
        .willReturn(Optional.of(depositPayment));
    given(bankAccounts.find(DEPOSIT_ACCOUNT_IBAN))
        .willReturn(
            Optional.of(new BankAccount(DEPOSIT_ACCOUNT_IBAN, DEPOSIT_EUR, TKF100, "gw-test")));

    processor.process(bankStatement, statementAccount);

    verify(payoutAccountReceiptLedger, never()).recordReturn(any(), any(), any());
    verify(savingFundPaymentRepository, never()).changeStatus(any(), any());
    verify(paymentCheckService)
        .record(
            PAYOUT_WITHOUT_REQUEST,
            HOLD,
            debit.getId().toString(),
            "a debit from the payout account matches no redemption request");
  }

  @Test
  void withdrawalOutgoing_returnOfADifferentAmountThanWasReceived_isNotBookedAsItsReturn() {
    var originalPayment =
        aPayment()
            .id(UUID.randomUUID())
            .amount(new BigDecimal("100.00"))
            .beneficiaryIban(WITHDRAWAL_ACCOUNT_IBAN)
            .status(RETURNED)
            .build();
    var endToEndId = endToEndIdConverter.toEndToEndId(originalPayment.getId());
    var debit =
        aPayment()
            .amount(new BigDecimal("-90.00"))
            .beneficiaryIban(EXTERNAL_ACCOUNT_IBAN)
            .endToEndId(endToEndId)
            .receivedBefore(Instant.parse("2026-11-26T09:00:00Z"))
            .build();
    var bankStatement =
        setupMocksForPaymentWithAccount(debit, WITHDRAWAL_ACCOUNT_IBAN, WITHDRAWAL_EUR);
    given(savingFundPaymentRepository.findOriginalPaymentForReturn(endToEndId))
        .willReturn(Optional.of(originalPayment));
    given(bankAccounts.find(WITHDRAWAL_ACCOUNT_IBAN))
        .willReturn(
            Optional.of(
                new BankAccount(WITHDRAWAL_ACCOUNT_IBAN, WITHDRAWAL_EUR, TKF100, "gw-test")));

    processor.process(bankStatement, statementAccount);

    verify(payoutAccountReceiptLedger, never()).recordReturn(any(), any(), any());
    verify(paymentCheckService)
        .record(
            PAYOUT_WITHOUT_REQUEST,
            HOLD,
            debit.getId().toString(),
            "a debit from the payout account matches no redemption request");
  }
}
