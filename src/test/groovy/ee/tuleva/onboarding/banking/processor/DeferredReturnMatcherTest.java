package ee.tuleva.onboarding.banking.processor;

import static ee.tuleva.onboarding.auth.UserFixture.sampleUser;
import static ee.tuleva.onboarding.savings.fund.SavingFundPayment.Status.*;
import static ee.tuleva.onboarding.savings.fund.SavingFundPaymentFixture.aPayment;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import ee.tuleva.onboarding.banking.event.BankMessageEvents.BankMessagesProcessingCompleted;
import ee.tuleva.onboarding.ledger.SavingsFundLedger;
import ee.tuleva.onboarding.savings.fund.SavingFundPaymentRepository;
import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.user.UserService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DeferredReturnMatcherTest {

  @Mock SavingFundPaymentRepository savingFundPaymentRepository;
  @Mock SavingsFundLedger savingsFundLedger;
  @Mock UserService userService;

  @InjectMocks DeferredReturnMatcher deferredReturnMatcher;

  @Test
  void matchesUserCancelledReturn() {
    User user = sampleUser().build();
    var originalPaymentId = UUID.randomUUID();
    var endToEndId = originalPaymentId.toString().replace("-", "");
    var originalPayment =
        aPayment()
            .id(originalPaymentId)
            .userId(user.getId())
            .amount(new BigDecimal("50.00"))
            .status(RETURNED)
            .build();
    var returnPayment =
        aPayment()
            .amount(new BigDecimal("-50.00"))
            .beneficiaryIban("EE112233445566778899")
            .endToEndId(endToEndId)
            .build();
    when(savingFundPaymentRepository.findUnmatchedOutgoingReturns())
        .thenReturn(List.of(returnPayment));
    when(savingFundPaymentRepository.findOriginalPaymentForReturn(endToEndId))
        .thenReturn(Optional.of(originalPayment));
    when(savingsFundLedger.hasLedgerEntry(originalPaymentId)).thenReturn(false);
    when(userService.getByIdOrThrow(user.getId())).thenReturn(user);

    deferredReturnMatcher.onBankMessagesProcessed(new BankMessagesProcessingCompleted());

    verify(savingsFundLedger)
        .recordPaymentCancelled(user, new BigDecimal("50.00"), originalPaymentId);
  }

  @Test
  void matchesUnattributedBounceBack() {
    var originalPaymentId = UUID.randomUUID();
    var endToEndId = originalPaymentId.toString().replace("-", "");
    var originalPayment =
        aPayment()
            .id(originalPaymentId)
            .userId(null)
            .amount(new BigDecimal("75.00"))
            .status(TO_BE_RETURNED)
            .build();
    var returnPayment =
        aPayment()
            .amount(new BigDecimal("-75.00"))
            .beneficiaryIban("EE112233445566778899")
            .endToEndId(endToEndId)
            .build();
    when(savingFundPaymentRepository.findUnmatchedOutgoingReturns())
        .thenReturn(List.of(returnPayment));
    when(savingFundPaymentRepository.findOriginalPaymentForReturn(endToEndId))
        .thenReturn(Optional.of(originalPayment));
    when(savingsFundLedger.hasLedgerEntry(originalPaymentId)).thenReturn(false);

    deferredReturnMatcher.onBankMessagesProcessed(new BankMessagesProcessingCompleted());

    verify(savingsFundLedger)
        .bounceBackUnattributedPayment(new BigDecimal("75.00"), originalPaymentId);
  }

  @Test
  void fallsBackToIbanAndAmountMatch() {
    var originalPaymentId = UUID.randomUUID();
    var originalPayment =
        aPayment()
            .id(originalPaymentId)
            .userId(null)
            .amount(new BigDecimal("75.00"))
            .status(RECEIVED)
            .build();
    var returnPayment =
        aPayment()
            .amount(new BigDecimal("-75.00"))
            .beneficiaryIban("EE112233445566778899")
            .endToEndId(null)
            .build();
    when(savingFundPaymentRepository.findUnmatchedOutgoingReturns())
        .thenReturn(List.of(returnPayment));
    when(savingFundPaymentRepository.findOriginalPaymentForReturn(any()))
        .thenReturn(Optional.empty());
    when(savingFundPaymentRepository.findOriginalPaymentByIbanAndAmount(
            "EE112233445566778899", new BigDecimal("-75.00")))
        .thenReturn(Optional.of(originalPayment));
    when(savingsFundLedger.hasLedgerEntry(originalPaymentId)).thenReturn(false);

    deferredReturnMatcher.onBankMessagesProcessed(new BankMessagesProcessingCompleted());

    verify(savingsFundLedger)
        .bounceBackUnattributedPayment(new BigDecimal("75.00"), originalPaymentId);
  }

  @Test
  void skipsWhenReturnLedgerEntryAlreadyExists() {
    var originalPaymentId = UUID.randomUUID();
    var endToEndId = originalPaymentId.toString().replace("-", "");
    var originalPayment =
        aPayment().id(originalPaymentId).userId(null).amount(new BigDecimal("50.00")).build();
    var returnPayment = aPayment().amount(new BigDecimal("-50.00")).endToEndId(endToEndId).build();
    when(savingFundPaymentRepository.findUnmatchedOutgoingReturns())
        .thenReturn(List.of(returnPayment));
    when(savingFundPaymentRepository.findOriginalPaymentForReturn(endToEndId))
        .thenReturn(Optional.of(originalPayment));
    when(savingsFundLedger.hasLedgerEntry(originalPaymentId)).thenReturn(true);

    deferredReturnMatcher.onBankMessagesProcessed(new BankMessagesProcessingCompleted());

    verify(savingsFundLedger, never()).recordPaymentCancelled(any(), any(), any());
    verify(savingsFundLedger, never()).bounceBackUnattributedPayment(any(), any());
  }

  @Test
  void noOpWhenNoUnmatchedReturns() {
    when(savingFundPaymentRepository.findUnmatchedOutgoingReturns()).thenReturn(List.of());

    deferredReturnMatcher.onBankMessagesProcessed(new BankMessagesProcessingCompleted());

    verify(savingsFundLedger, never()).recordPaymentCancelled(any(), any(), any());
    verify(savingsFundLedger, never()).bounceBackUnattributedPayment(any(), any());
  }

  @Test
  void matchedReturn_transitionsOriginalFromToBeReturnedToReturned() {
    var originalPaymentId = UUID.randomUUID();
    var endToEndId = originalPaymentId.toString().replace("-", "");
    var originalPayment =
        aPayment()
            .id(originalPaymentId)
            .userId(null)
            .amount(new BigDecimal("75.00"))
            .status(TO_BE_RETURNED)
            .build();
    var returnPayment =
        aPayment()
            .amount(new BigDecimal("-75.00"))
            .beneficiaryIban("EE112233445566778899")
            .endToEndId(endToEndId)
            .build();
    when(savingFundPaymentRepository.findUnmatchedOutgoingReturns())
        .thenReturn(List.of(returnPayment));
    when(savingFundPaymentRepository.findOriginalPaymentForReturn(endToEndId))
        .thenReturn(Optional.of(originalPayment));
    when(savingsFundLedger.hasLedgerEntry(originalPaymentId)).thenReturn(false);

    deferredReturnMatcher.onBankMessagesProcessed(new BankMessagesProcessingCompleted());

    verify(savingFundPaymentRepository).changeStatus(originalPaymentId, RETURNED);
  }

  @Test
  void matchedReturn_transitionsOriginalFromReceivedThroughToBeReturnedToReturned() {
    var originalPaymentId = UUID.randomUUID();
    var endToEndId = originalPaymentId.toString().replace("-", "");
    var originalPayment =
        aPayment()
            .id(originalPaymentId)
            .userId(null)
            .amount(new BigDecimal("18472.00"))
            .status(RECEIVED)
            .build();
    var returnPayment =
        aPayment()
            .amount(new BigDecimal("-18472.00"))
            .beneficiaryIban("EE112233445566778899")
            .endToEndId(endToEndId)
            .build();
    when(savingFundPaymentRepository.findUnmatchedOutgoingReturns())
        .thenReturn(List.of(returnPayment));
    when(savingFundPaymentRepository.findOriginalPaymentForReturn(endToEndId))
        .thenReturn(Optional.of(originalPayment));
    when(savingsFundLedger.hasLedgerEntry(originalPaymentId)).thenReturn(false);

    deferredReturnMatcher.onBankMessagesProcessed(new BankMessagesProcessingCompleted());

    var inOrder = inOrder(savingFundPaymentRepository);
    inOrder.verify(savingFundPaymentRepository).changeStatus(originalPaymentId, TO_BE_RETURNED);
    inOrder.verify(savingFundPaymentRepository).changeStatus(originalPaymentId, RETURNED);
  }

  @Test
  void matchedReturn_transitionsOriginalFromVerifiedThroughToBeReturnedToReturned() {
    User user = sampleUser().build();
    var originalPaymentId = UUID.randomUUID();
    var endToEndId = originalPaymentId.toString().replace("-", "");
    var originalPayment =
        aPayment()
            .id(originalPaymentId)
            .userId(user.getId())
            .amount(new BigDecimal("50.00"))
            .status(VERIFIED)
            .build();
    var returnPayment =
        aPayment()
            .amount(new BigDecimal("-50.00"))
            .beneficiaryIban("EE112233445566778899")
            .endToEndId(endToEndId)
            .build();
    when(savingFundPaymentRepository.findUnmatchedOutgoingReturns())
        .thenReturn(List.of(returnPayment));
    when(savingFundPaymentRepository.findOriginalPaymentForReturn(endToEndId))
        .thenReturn(Optional.of(originalPayment));
    when(savingsFundLedger.hasLedgerEntry(originalPaymentId)).thenReturn(false);
    when(userService.getByIdOrThrow(user.getId())).thenReturn(user);

    deferredReturnMatcher.onBankMessagesProcessed(new BankMessagesProcessingCompleted());

    var inOrder = inOrder(savingFundPaymentRepository);
    inOrder.verify(savingFundPaymentRepository).changeStatus(originalPaymentId, TO_BE_RETURNED);
    inOrder.verify(savingFundPaymentRepository).changeStatus(originalPaymentId, RETURNED);
  }

  @Test
  void matchedReturn_skipsStatusChangeWhenAlreadyReturned() {
    User user = sampleUser().build();
    var originalPaymentId = UUID.randomUUID();
    var endToEndId = originalPaymentId.toString().replace("-", "");
    var originalPayment =
        aPayment()
            .id(originalPaymentId)
            .userId(user.getId())
            .amount(new BigDecimal("50.00"))
            .status(RETURNED)
            .build();
    var returnPayment =
        aPayment()
            .amount(new BigDecimal("-50.00"))
            .beneficiaryIban("EE112233445566778899")
            .endToEndId(endToEndId)
            .build();
    when(savingFundPaymentRepository.findUnmatchedOutgoingReturns())
        .thenReturn(List.of(returnPayment));
    when(savingFundPaymentRepository.findOriginalPaymentForReturn(endToEndId))
        .thenReturn(Optional.of(originalPayment));
    when(savingsFundLedger.hasLedgerEntry(originalPaymentId)).thenReturn(false);
    when(userService.getByIdOrThrow(user.getId())).thenReturn(user);

    deferredReturnMatcher.onBankMessagesProcessed(new BankMessagesProcessingCompleted());

    verify(savingFundPaymentRepository, never()).changeStatus(any(), any());
  }

  @Test
  void matchedReturn_addsReturnReasonForVerifiedPayments() {
    User user = sampleUser().build();
    var originalPaymentId = UUID.randomUUID();
    var endToEndId = originalPaymentId.toString().replace("-", "");
    var originalPayment =
        aPayment()
            .id(originalPaymentId)
            .userId(user.getId())
            .amount(new BigDecimal("50.00"))
            .status(VERIFIED)
            .returnReason(null)
            .build();
    var returnPayment =
        aPayment()
            .amount(new BigDecimal("-50.00"))
            .beneficiaryIban("EE112233445566778899")
            .endToEndId(endToEndId)
            .build();
    when(savingFundPaymentRepository.findUnmatchedOutgoingReturns())
        .thenReturn(List.of(returnPayment));
    when(savingFundPaymentRepository.findOriginalPaymentForReturn(endToEndId))
        .thenReturn(Optional.of(originalPayment));
    when(savingsFundLedger.hasLedgerEntry(originalPaymentId)).thenReturn(false);
    when(userService.getByIdOrThrow(user.getId())).thenReturn(user);

    deferredReturnMatcher.onBankMessagesProcessed(new BankMessagesProcessingCompleted());

    verify(savingFundPaymentRepository).addReturnReason(eq(originalPaymentId), any());
  }

  @Test
  void originalNotFound_doesNotCreateLedgerEntry() {
    var returnPayment =
        aPayment()
            .amount(new BigDecimal("-100.00"))
            .beneficiaryIban("EE112233445566778899")
            .endToEndId("nonexistent12345678901234567890")
            .build();
    when(savingFundPaymentRepository.findUnmatchedOutgoingReturns())
        .thenReturn(List.of(returnPayment));
    when(savingFundPaymentRepository.findOriginalPaymentForReturn(any()))
        .thenReturn(Optional.empty());
    when(savingFundPaymentRepository.findOriginalPaymentByIbanAndAmount(any(), any()))
        .thenReturn(Optional.empty());

    deferredReturnMatcher.onBankMessagesProcessed(new BankMessagesProcessingCompleted());

    verify(savingsFundLedger, never()).recordPaymentCancelled(any(), any(), any());
    verify(savingsFundLedger, never()).bounceBackUnattributedPayment(any(), any());
  }
}
