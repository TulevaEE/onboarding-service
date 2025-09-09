package ee.tuleva.onboarding.capital.transfer.execution;

import static ee.tuleva.onboarding.capital.event.member.MemberCapitalEventType.*;
import static ee.tuleva.onboarding.capital.transfer.CapitalTransferContractState.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class CapitalTransferExecutorTest {

  @Mock private CapitalTransferContractRepository contractRepository;
  @Mock private MemberCapitalEventRepository memberCapitalEventRepository;
  @Mock private AggregatedCapitalEventRepository aggregatedCapitalEventRepository;
  @Mock private CapitalTransferValidator validator;
  @Mock private CapitalTransferEventLinkRepository linkRepository;

  @Mock private CapitalTransferContract contract;
  @Mock private Member seller;
  @Mock private Member buyer;
  @Mock private AggregatedCapitalEvent aggregatedEvent;

  private CapitalTransferExecutor executor;

  private final BigDecimal OWNERSHIP_UNIT_PRICE = new BigDecimal("1.25000");
  private final BigDecimal BOOK_VALUE = new BigDecimal("100.00000");
  private final BigDecimal EXPECTED_UNITS = new BigDecimal("80.00000"); // 100 / 1.25

  @BeforeEach
  public void setUp() {
    executor =
        new CapitalTransferExecutor(
            contractRepository,
            memberCapitalEventRepository,
            aggregatedCapitalEventRepository,
            validator,
            linkRepository);
  }

  @Test
  @DisplayName("Should execute transfer successfully with single capital type")
  public void whenExecuteTransfer_withValidContract_thenSuccessfullyExecutes() {
    // Given
    when(contract.getId()).thenReturn(1L);
    when(contract.getSeller()).thenReturn(seller);
    when(contract.getBuyer()).thenReturn(buyer);
    when(seller.getId()).thenReturn(101L);
    when(buyer.getId()).thenReturn(102L);
    when(aggregatedEvent.getOwnershipUnitPrice()).thenReturn(OWNERSHIP_UNIT_PRICE);
    when(aggregatedCapitalEventRepository.findTopByOrderByDateDesc()).thenReturn(aggregatedEvent);

    when(memberCapitalEventRepository.save(any(MemberCapitalEvent.class)))
        .thenAnswer(
            invocation -> {
              MemberCapitalEvent event = invocation.getArgument(0);
              event.setId(System.nanoTime());
              return event;
            });

    CapitalTransferAmount transferAmount =
        new CapitalTransferAmount(CAPITAL_PAYMENT, new BigDecimal("125.00"), BOOK_VALUE);
    when(contract.getTransferAmounts()).thenReturn(List.of(transferAmount));

    // When
    executor.execute(contract);

    // Then
    verify(validator).validateContract(contract);
    verify(validator).validateSufficientCapital(contract);

    // Verify seller withdrawal event
    ArgumentCaptor<MemberCapitalEvent> eventCaptor =
        ArgumentCaptor.forClass(MemberCapitalEvent.class);
    verify(memberCapitalEventRepository, times(2)).save(eventCaptor.capture());

    // Verify links were created
    verify(linkRepository, times(2)).save(any(CapitalTransferEventLink.class));

    List<MemberCapitalEvent> savedEvents = eventCaptor.getAllValues();

    // First event should be seller withdrawal
    MemberCapitalEvent sellerEvent = savedEvents.get(0);
    assertThat(sellerEvent.getMember()).isEqualTo(seller);
    assertThat(sellerEvent.getType()).isEqualTo(CAPITAL_PAYMENT);
    assertThat(sellerEvent.getFiatValue()).isEqualTo(BOOK_VALUE.negate());
    assertThat(sellerEvent.getOwnershipUnitAmount()).isEqualTo(EXPECTED_UNITS.negate());
    assertThat(sellerEvent.getAccountingDate())
        .isEqualTo(LocalDate.now(ZoneId.of("Europe/Tallinn")));
    assertThat(sellerEvent.getEffectiveDate())
        .isEqualTo(LocalDate.now(ZoneId.of("Europe/Tallinn")));

    // Second event should be buyer acquisition
    MemberCapitalEvent buyerEvent = savedEvents.get(1);
    assertThat(buyerEvent.getMember()).isEqualTo(buyer);
    assertThat(buyerEvent.getType()).isEqualTo(CAPITAL_ACQUIRED);
    assertThat(buyerEvent.getFiatValue()).isEqualTo(BOOK_VALUE);
    assertThat(buyerEvent.getOwnershipUnitAmount()).isEqualTo(EXPECTED_UNITS);
    assertThat(buyerEvent.getAccountingDate())
        .isEqualTo(LocalDate.now(ZoneId.of("Europe/Tallinn")));
    assertThat(buyerEvent.getEffectiveDate()).isEqualTo(LocalDate.now(ZoneId.of("Europe/Tallinn")));

    // Verify contract state updated to EXECUTED
    verify(contract).executed();
    verify(contractRepository).save(contract);
  }

  @Test
  @DisplayName("Should execute transfer with multiple capital types")
  public void whenExecuteTransfer_withMultipleTypes_thenAllTypesTransferred() {
    // Given
    when(contract.getId()).thenReturn(1L);
    when(contract.getSeller()).thenReturn(seller);
    when(contract.getBuyer()).thenReturn(buyer);
    when(seller.getId()).thenReturn(101L);
    when(buyer.getId()).thenReturn(102L);
    when(aggregatedEvent.getOwnershipUnitPrice()).thenReturn(OWNERSHIP_UNIT_PRICE);
    when(aggregatedCapitalEventRepository.findTopByOrderByDateDesc()).thenReturn(aggregatedEvent);

    when(memberCapitalEventRepository.save(any(MemberCapitalEvent.class)))
        .thenAnswer(
            invocation -> {
              MemberCapitalEvent event = invocation.getArgument(0);
              event.setId(System.nanoTime());
              return event;
            });

    CapitalTransferAmount payment =
        new CapitalTransferAmount(CAPITAL_PAYMENT, new BigDecimal("125.00"), BOOK_VALUE);
    CapitalTransferAmount bonus =
        new CapitalTransferAmount(
            MEMBERSHIP_BONUS, new BigDecimal("62.50"), new BigDecimal("50.00"));
    when(contract.getTransferAmounts()).thenReturn(List.of(payment, bonus));

    // When
    executor.execute(contract);

    // Then
    ArgumentCaptor<MemberCapitalEvent> eventCaptor =
        ArgumentCaptor.forClass(MemberCapitalEvent.class);
    verify(memberCapitalEventRepository, times(4))
        .save(eventCaptor.capture()); // 2 types × 2 events each

    // Verify links were created
    verify(linkRepository, times(4)).save(any(CapitalTransferEventLink.class));

    List<MemberCapitalEvent> savedEvents = eventCaptor.getAllValues();

    // Verify we have events for both types
    assertThat(savedEvents.stream().filter(e -> e.getType() == CAPITAL_PAYMENT).count())
        .isEqualTo(1);
    assertThat(savedEvents.stream().filter(e -> e.getType() == MEMBERSHIP_BONUS).count())
        .isEqualTo(1);
    assertThat(savedEvents.stream().filter(e -> e.getType() == CAPITAL_ACQUIRED).count())
        .isEqualTo(2);
  }

  @Test
  @DisplayName("Should skip transfers with zero book value")
  public void whenExecuteTransfer_withZeroBookValue_thenSkipsTransfer() {
    // Given
    when(contract.getId()).thenReturn(1L);
    when(contract.getSeller()).thenReturn(seller);
    when(contract.getBuyer()).thenReturn(buyer);
    when(seller.getId()).thenReturn(101L);
    when(buyer.getId()).thenReturn(102L);
    when(aggregatedEvent.getOwnershipUnitPrice()).thenReturn(OWNERSHIP_UNIT_PRICE);
    when(aggregatedCapitalEventRepository.findTopByOrderByDateDesc()).thenReturn(aggregatedEvent);

    when(memberCapitalEventRepository.save(any(MemberCapitalEvent.class)))
        .thenAnswer(
            invocation -> {
              MemberCapitalEvent event = invocation.getArgument(0);
              event.setId(System.nanoTime());
              return event;
            });

    CapitalTransferAmount zeroAmount =
        new CapitalTransferAmount(CAPITAL_PAYMENT, new BigDecimal("100.00"), BigDecimal.ZERO);
    CapitalTransferAmount validAmount =
        new CapitalTransferAmount(MEMBERSHIP_BONUS, new BigDecimal("125.00"), BOOK_VALUE);
    when(contract.getTransferAmounts()).thenReturn(List.of(zeroAmount, validAmount));
    when(validator.shouldSkipTransfer(zeroAmount)).thenReturn(true);
    when(validator.shouldSkipTransfer(validAmount)).thenReturn(false);

    // When
    executor.execute(contract);

    // Then
    ArgumentCaptor<MemberCapitalEvent> eventCaptor =
        ArgumentCaptor.forClass(MemberCapitalEvent.class);
    verify(memberCapitalEventRepository, times(2))
        .save(eventCaptor.capture()); // Only valid amount creates events

    // Verify links were created only for valid transfers
    verify(linkRepository, times(2)).save(any(CapitalTransferEventLink.class));

    List<MemberCapitalEvent> savedEvents = eventCaptor.getAllValues();
    assertThat(savedEvents.stream().anyMatch(e -> e.getType() == CAPITAL_PAYMENT)).isFalse();
    assertThat(savedEvents.stream().anyMatch(e -> e.getType() == MEMBERSHIP_BONUS)).isTrue();
  }

  @Test
  @DisplayName("Should throw exception when validation fails")
  public void whenExecuteTransfer_withInvalidContract_thenThrowsException() {
    // Given
    when(contract.getId()).thenReturn(1L);
    doThrow(new IllegalStateException("Contract not in APPROVED state"))
        .when(validator)
        .validateContract(contract);

    // When & Then
    assertThatThrownBy(() -> executor.execute(contract))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Contract not in APPROVED state");

    verify(memberCapitalEventRepository, never()).save(any());
    verify(contract, never()).executed();
    verify(contractRepository, never()).save(any());
  }

  @Test
  @DisplayName("Should throw exception when insufficient capital")
  public void whenExecuteTransfer_withInsufficientCapital_thenThrowsException() {
    // Given
    when(contract.getId()).thenReturn(1L);
    doThrow(new IllegalStateException("Seller has insufficient CAPITAL_PAYMENT capital"))
        .when(validator)
        .validateSufficientCapital(contract);

    // When & Then
    assertThatThrownBy(() -> executor.execute(contract))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Seller has insufficient CAPITAL_PAYMENT capital");

    verify(memberCapitalEventRepository, never()).save(any());
    verify(contract, never()).executed();
    verify(contractRepository, never()).save(any());
  }

  @Test
  @DisplayName("Should throw exception when ownership unit price not available")
  public void whenExecuteTransfer_withNoOwnershipUnitPrice_thenThrowsException() {
    // Given
    when(contract.getId()).thenReturn(1L);
    when(aggregatedCapitalEventRepository.findTopByOrderByDateDesc()).thenReturn(null);

    // When & Then
    assertThatThrownBy(() -> executor.execute(contract))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Could not determine current ownership unit price");

    verify(memberCapitalEventRepository, never()).save(any());
    verify(contract, never()).executed();
    verify(contractRepository, never()).save(any());
  }

  @Test
  @DisplayName("Should throw exception when ownership unit price is null")
  public void whenExecuteTransfer_withNullOwnershipUnitPrice_thenThrowsException() {
    // Given
    when(contract.getId()).thenReturn(1L);
    when(aggregatedEvent.getOwnershipUnitPrice()).thenReturn(null);
    when(aggregatedCapitalEventRepository.findTopByOrderByDateDesc()).thenReturn(aggregatedEvent);

    // When & Then
    assertThatThrownBy(() -> executor.execute(contract))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Could not determine current ownership unit price");

    verify(memberCapitalEventRepository, never()).save(any());
    verify(contract, never()).executed();
    verify(contractRepository, never()).save(any());
  }
}
