package ee.tuleva.onboarding.capital.transfer.execution;

import static ee.tuleva.onboarding.capital.event.member.MemberCapitalEventType.*;
import static ee.tuleva.onboarding.capital.transfer.CapitalTransferContractState.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import ee.tuleva.onboarding.capital.event.AggregatedCapitalEvent;
import ee.tuleva.onboarding.capital.event.AggregatedCapitalEventRepository;
import ee.tuleva.onboarding.capital.event.member.MemberCapitalEvent;
import ee.tuleva.onboarding.capital.event.member.MemberCapitalEventRepository;
import ee.tuleva.onboarding.capital.transfer.CapitalTransferContract;
import ee.tuleva.onboarding.capital.transfer.CapitalTransferContract.CapitalTransferAmount;
import ee.tuleva.onboarding.capital.transfer.CapitalTransferContractRepository;
import ee.tuleva.onboarding.user.member.Member;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("Capital Transfer Execution Flow Tests")
class CapitalTransferExecutionFlowTest {

  @Mock private CapitalTransferContractRepository contractRepository;
  @Mock private MemberCapitalEventRepository memberCapitalEventRepository;
  @Mock private AggregatedCapitalEventRepository aggregatedCapitalEventRepository;
  @Mock private CapitalTransferValidator validator;
  @Mock private CapitalTransferEventLinkRepository linkRepository;

  @Mock private CapitalTransferContract contract;
  @Mock private Member sellerMember;
  @Mock private Member buyerMember;
  @Mock private AggregatedCapitalEvent aggregatedEvent;

  private CapitalTransferExecutor executor;
  private CapitalTransferExecutionJob executionJob;

  private final BigDecimal OWNERSHIP_UNIT_PRICE = new BigDecimal("1.25000");
  private final BigDecimal BOOK_VALUE = new BigDecimal("100.00000");

  @BeforeEach
  void setUp() {
    executor =
        new CapitalTransferExecutor(
            contractRepository,
            memberCapitalEventRepository,
            aggregatedCapitalEventRepository,
            validator,
            linkRepository);

    executionJob = new CapitalTransferExecutionJob(contractRepository, executor);
  }

  @Test
  @DisplayName("Complete flow: Job finds APPROVED contract and executes it successfully")
  void shouldExecuteCompleteCapitalTransferFlow() {
    // Given - Setup contract with multiple transfer amounts
    when(contract.getId()).thenReturn(1L);
    when(contract.getSeller()).thenReturn(sellerMember);
    when(contract.getBuyer()).thenReturn(buyerMember);
    when(sellerMember.getId()).thenReturn(101L);
    when(buyerMember.getId()).thenReturn(102L);

    CapitalTransferAmount payment =
        new CapitalTransferAmount(CAPITAL_PAYMENT, new BigDecimal("125.00"), BOOK_VALUE);
    CapitalTransferAmount bonus =
        new CapitalTransferAmount(
            MEMBERSHIP_BONUS, new BigDecimal("62.50"), new BigDecimal("50.00"));
    when(contract.getTransferAmounts()).thenReturn(List.of(payment, bonus));

    when(aggregatedEvent.getOwnershipUnitPrice()).thenReturn(OWNERSHIP_UNIT_PRICE);
    when(aggregatedCapitalEventRepository.findTopByOrderByDateDesc()).thenReturn(aggregatedEvent);

    // Mock job finding approved contracts
    when(contractRepository.findAllByState(APPROVED)).thenReturn(List.of(contract));

    // When - Execute the complete flow through the job
    executionJob.executeApprovedContracts();

    // Then - Verify complete execution flow

    // 1. Verify contract validation was called
    verify(validator).validateContract(contract);
    verify(validator).validateSufficientCapital(contract);

    // 2. Verify ownership unit price lookup
    verify(aggregatedCapitalEventRepository).findTopByOrderByDateDesc();

    // 3. Verify capital events were created (4 total: 2 seller withdrawals + 2 buyer acquisitions)
    ArgumentCaptor<MemberCapitalEvent> eventCaptor =
        ArgumentCaptor.forClass(MemberCapitalEvent.class);
    verify(memberCapitalEventRepository, times(4)).save(eventCaptor.capture());

    List<MemberCapitalEvent> savedEvents = eventCaptor.getAllValues();

    // Verify seller withdrawal events
    List<MemberCapitalEvent> sellerEvents =
        savedEvents.stream().filter(event -> event.getMember().equals(sellerMember)).toList();
    assertThat(sellerEvents).hasSize(2);

    // Verify CAPITAL_PAYMENT withdrawal
    MemberCapitalEvent paymentWithdrawal =
        sellerEvents.stream()
            .filter(event -> event.getType() == CAPITAL_PAYMENT)
            .findFirst()
            .orElseThrow();
    assertThat(paymentWithdrawal.getFiatValue()).isEqualByComparingTo(new BigDecimal("-100.00000"));
    assertThat(paymentWithdrawal.getOwnershipUnitAmount())
        .isEqualByComparingTo(new BigDecimal("-80.00000"));

    // Verify MEMBERSHIP_BONUS withdrawal
    MemberCapitalEvent bonusWithdrawal =
        sellerEvents.stream()
            .filter(event -> event.getType() == MEMBERSHIP_BONUS)
            .findFirst()
            .orElseThrow();
    assertThat(bonusWithdrawal.getFiatValue()).isEqualByComparingTo(new BigDecimal("-50.00000"));
    assertThat(bonusWithdrawal.getOwnershipUnitAmount())
        .isEqualByComparingTo(new BigDecimal("-40.00000"));

    // Verify buyer acquisition events
    List<MemberCapitalEvent> buyerEvents =
        savedEvents.stream().filter(event -> event.getMember().equals(buyerMember)).toList();
    assertThat(buyerEvents).hasSize(2);
    assertThat(buyerEvents).allMatch(event -> event.getType() == CAPITAL_ACQUIRED);
    assertThat(buyerEvents).allMatch(event -> event.getFiatValue().compareTo(BigDecimal.ZERO) > 0);

    // 4. Verify contract state was updated
    verify(contract).executed();
    verify(contractRepository).save(contract);
  }

  @Test
  @DisplayName("Flow handles zero book value transfers correctly")
  void shouldSkipZeroBookValueTransfersInFlow() {
    // Given - Contract with zero and valid transfers
    when(contract.getId()).thenReturn(1L);
    when(contract.getSeller()).thenReturn(sellerMember);
    when(contract.getBuyer()).thenReturn(buyerMember);
    when(sellerMember.getId()).thenReturn(101L);
    when(buyerMember.getId()).thenReturn(102L);
    when(aggregatedEvent.getOwnershipUnitPrice()).thenReturn(OWNERSHIP_UNIT_PRICE);
    when(aggregatedCapitalEventRepository.findTopByOrderByDateDesc()).thenReturn(aggregatedEvent);

    CapitalTransferAmount zeroAmount =
        new CapitalTransferAmount(CAPITAL_PAYMENT, new BigDecimal("100.00"), BigDecimal.ZERO);
    CapitalTransferAmount validAmount =
        new CapitalTransferAmount(
            MEMBERSHIP_BONUS, new BigDecimal("25.00"), new BigDecimal("20.00"));
    when(contract.getTransferAmounts()).thenReturn(List.of(zeroAmount, validAmount));

    // Mock validator behavior
    when(validator.shouldSkipTransfer(zeroAmount)).thenReturn(true);
    when(validator.shouldSkipTransfer(validAmount)).thenReturn(false);

    when(contractRepository.findAllByState(APPROVED)).thenReturn(List.of(contract));

    // When
    executionJob.executeApprovedContracts();

    // Then - Only 2 events should be created (1 seller + 1 buyer for the valid transfer)
    verify(memberCapitalEventRepository, times(2)).save(any(MemberCapitalEvent.class));
    verify(contract).executed();
    verify(contractRepository).save(contract);
  }

  @Test
  @DisplayName("Flow handles validation failures gracefully")
  void shouldHandleValidationFailuresInFlow() {
    // Given
    when(contract.getId()).thenReturn(1L);
    when(contractRepository.findAllByState(APPROVED)).thenReturn(List.of(contract));

    // Mock validation failure
    doThrow(new IllegalStateException("Insufficient capital"))
        .when(validator)
        .validateSufficientCapital(contract);

    // When
    executionJob.executeApprovedContracts();

    // Then - No events should be created, no state change
    verify(memberCapitalEventRepository, never()).save(any());
    verify(contract, never()).executed();
    verify(contractRepository, never()).save(any());
  }

  @Test
  @DisplayName("Flow processes multiple contracts in batch")
  void shouldProcessMultipleContractsInFlow() {
    // Given - Multiple contracts
    CapitalTransferContract contract1 = mock(CapitalTransferContract.class);
    CapitalTransferContract contract2 = mock(CapitalTransferContract.class);

    // Setup contract1
    when(contract1.getId()).thenReturn(1L);
    when(contract1.getSeller()).thenReturn(sellerMember);
    when(contract1.getBuyer()).thenReturn(buyerMember);
    when(contract1.getTransferAmounts())
        .thenReturn(
            List.of(
                new CapitalTransferAmount(CAPITAL_PAYMENT, new BigDecimal("125.00"), BOOK_VALUE)));

    // Setup contract2
    when(contract2.getId()).thenReturn(2L);
    when(contract2.getSeller()).thenReturn(sellerMember);
    when(contract2.getBuyer()).thenReturn(buyerMember);
    when(contract2.getTransferAmounts())
        .thenReturn(
            List.of(
                new CapitalTransferAmount(
                    MEMBERSHIP_BONUS, new BigDecimal("62.50"), new BigDecimal("50.00"))));

    // Setup aggregated event repository and member IDs for both contracts
    when(sellerMember.getId()).thenReturn(101L);
    when(buyerMember.getId()).thenReturn(102L);
    when(aggregatedEvent.getOwnershipUnitPrice()).thenReturn(OWNERSHIP_UNIT_PRICE);
    when(aggregatedCapitalEventRepository.findTopByOrderByDateDesc()).thenReturn(aggregatedEvent);

    when(contractRepository.findAllByState(APPROVED)).thenReturn(List.of(contract1, contract2));

    // When
    executionJob.executeApprovedContracts();

    // Then - Both contracts should be processed
    verify(validator, times(2)).validateContract(any());
    verify(validator, times(2)).validateSufficientCapital(any());

    // 4 events total (2 per contract: 1 seller + 1 buyer each)
    verify(memberCapitalEventRepository, times(4)).save(any(MemberCapitalEvent.class));

    verify(contract1).executed();
    verify(contract2).executed();
    verify(contractRepository).save(contract1);
    verify(contractRepository).save(contract2);
  }

  @Test
  @DisplayName("Flow handles mixed success and failure scenarios")
  void shouldHandleMixedSuccessAndFailureInFlow() {
    // Given - One successful, one failing contract
    CapitalTransferContract successContract = mock(CapitalTransferContract.class);
    CapitalTransferContract failContract = mock(CapitalTransferContract.class);

    // Setup success contract
    when(successContract.getId()).thenReturn(1L);
    when(successContract.getSeller()).thenReturn(sellerMember);
    when(successContract.getBuyer()).thenReturn(buyerMember);
    when(successContract.getTransferAmounts())
        .thenReturn(
            List.of(
                new CapitalTransferAmount(CAPITAL_PAYMENT, new BigDecimal("125.00"), BOOK_VALUE)));

    // Setup failing contract
    when(failContract.getId()).thenReturn(2L);

    when(contractRepository.findAllByState(APPROVED))
        .thenReturn(List.of(successContract, failContract));

    // Setup the successful contract to execute properly
    when(successContract.getSeller()).thenReturn(sellerMember);
    when(successContract.getBuyer()).thenReturn(buyerMember);
    when(sellerMember.getId()).thenReturn(101L);
    when(buyerMember.getId()).thenReturn(102L);
    when(aggregatedEvent.getOwnershipUnitPrice()).thenReturn(OWNERSHIP_UNIT_PRICE);
    when(aggregatedCapitalEventRepository.findTopByOrderByDateDesc()).thenReturn(aggregatedEvent);

    // Make the second contract fail validation
    doNothing().when(validator).validateContract(successContract);
    doNothing().when(validator).validateSufficientCapital(successContract);
    doThrow(new IllegalStateException("Insufficient capital"))
        .when(validator)
        .validateContract(failContract);

    // When
    executionJob.executeApprovedContracts();

    // Then - Only successful contract should be processed
    verify(memberCapitalEventRepository, times(2))
        .save(any(MemberCapitalEvent.class)); // Only success contract
    verify(successContract).executed();
    verify(contractRepository).save(successContract);

    // Failed contract should not be updated
    verify(failContract, never()).executed();
    verify(contractRepository, never()).save(failContract);
  }

  @Test
  @DisplayName("Flow handles no approved contracts gracefully")
  void shouldHandleNoApprovedContractsInFlow() {
    // Given
    when(contractRepository.findAllByState(APPROVED)).thenReturn(List.of());

    // When
    executionJob.executeApprovedContracts();

    // Then - No processing should occur
    verify(validator, never()).validateContract(any());
    verify(memberCapitalEventRepository, never()).save(any());
    verify(contractRepository, never()).save(any());
  }

  @Test
  @DisplayName("Flow calculates ownership units correctly based on unit price")
  void shouldCalculateOwnershipUnitsCorrectlyInFlow() {
    // Given - Contract with specific amounts for precise calculation testing
    when(contract.getId()).thenReturn(1L);
    when(contract.getSeller()).thenReturn(sellerMember);
    when(contract.getBuyer()).thenReturn(buyerMember);
    when(sellerMember.getId()).thenReturn(101L);
    when(buyerMember.getId()).thenReturn(102L);
    when(aggregatedEvent.getOwnershipUnitPrice()).thenReturn(OWNERSHIP_UNIT_PRICE);
    when(aggregatedCapitalEventRepository.findTopByOrderByDateDesc()).thenReturn(aggregatedEvent);

    CapitalTransferAmount transferAmount =
        new CapitalTransferAmount(
            CAPITAL_PAYMENT,
            new BigDecimal("250.00"),
            new BigDecimal("200.00") // 200 / 1.25 = 160 units
            );
    when(contract.getTransferAmounts()).thenReturn(List.of(transferAmount));
    when(contractRepository.findAllByState(APPROVED)).thenReturn(List.of(contract));

    // When
    executionJob.executeApprovedContracts();

    // Then - Verify precise unit calculations
    ArgumentCaptor<MemberCapitalEvent> eventCaptor =
        ArgumentCaptor.forClass(MemberCapitalEvent.class);
    verify(memberCapitalEventRepository, times(2)).save(eventCaptor.capture());

    List<MemberCapitalEvent> savedEvents = eventCaptor.getAllValues();

    // Seller withdrawal should have -160 units
    MemberCapitalEvent sellerEvent =
        savedEvents.stream()
            .filter(event -> event.getMember().equals(sellerMember))
            .findFirst()
            .orElseThrow();
    assertThat(sellerEvent.getOwnershipUnitAmount())
        .isEqualByComparingTo(new BigDecimal("-160.00000"));
    assertThat(sellerEvent.getFiatValue()).isEqualByComparingTo(new BigDecimal("-200.00000"));

    // Buyer acquisition should have +160 units
    MemberCapitalEvent buyerEvent =
        savedEvents.stream()
            .filter(event -> event.getMember().equals(buyerMember))
            .findFirst()
            .orElseThrow();
    assertThat(buyerEvent.getOwnershipUnitAmount())
        .isEqualByComparingTo(new BigDecimal("160.00000"));
    assertThat(buyerEvent.getFiatValue()).isEqualByComparingTo(new BigDecimal("200.00000"));

    // Verify contract state was updated
    verify(contract).executed();
    verify(contractRepository).save(contract);
  }
}
