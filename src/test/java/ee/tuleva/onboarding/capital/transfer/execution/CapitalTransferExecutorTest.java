package ee.tuleva.onboarding.capital.transfer.execution;

import static ee.tuleva.onboarding.capital.event.member.MemberCapitalEventType.*;
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

  private final BigDecimal OWNERSHIP_UNIT_PRICE = new BigDecimal("1.73456");
  private final BigDecimal BOOK_VALUE = new BigDecimal("123.45000");
  private final BigDecimal EXPECTED_UNITS =
      new BigDecimal("71.17079"); // 123.45 / 1.73456 = 71.17079 (rounded to 5 decimal places)

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

    BigDecimal sellerTotalFiatValue = new BigDecimal("987.65432");
    BigDecimal sellerTotalUnits = new BigDecimal("321.98765");
    when(memberCapitalEventRepository.getTotalFiatValueByMemberIdAndType(101L, CAPITAL_PAYMENT))
        .thenReturn(sellerTotalFiatValue);
    when(memberCapitalEventRepository.getTotalOwnershipUnitsByMemberIdAndType(
            101L, CAPITAL_PAYMENT))
        .thenReturn(sellerTotalUnits);

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

    // Calculate expected proportional fiat value: (totalFiatValue * unitsToTransfer) / totalUnits
    // (987.65432 * 71.17079) / 321.98765 = 218.30694 (rounded to 5 decimal places)
    BigDecimal expectedProportionalFiatValue = new BigDecimal("218.30694");

    // First event should be seller withdrawal
    MemberCapitalEvent sellerEvent = savedEvents.get(0);
    assertThat(sellerEvent.getMember()).isEqualTo(seller);
    assertThat(sellerEvent.getType()).isEqualTo(CAPITAL_PAYMENT);
    assertThat(sellerEvent.getFiatValue()).isEqualTo(expectedProportionalFiatValue.negate());
    assertThat(sellerEvent.getOwnershipUnitAmount()).isEqualTo(EXPECTED_UNITS.negate());
    assertThat(sellerEvent.getAccountingDate())
        .isEqualTo(LocalDate.now(ZoneId.of("Europe/Tallinn")));
    assertThat(sellerEvent.getEffectiveDate())
        .isEqualTo(LocalDate.now(ZoneId.of("Europe/Tallinn")));

    // Second event should be buyer acquisition
    MemberCapitalEvent buyerEvent = savedEvents.get(1);
    assertThat(buyerEvent.getMember()).isEqualTo(buyer);
    assertThat(buyerEvent.getType()).isEqualTo(CAPITAL_ACQUIRED);
    assertThat(buyerEvent.getFiatValue()).isEqualTo(expectedProportionalFiatValue);
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

    when(memberCapitalEventRepository.getTotalFiatValueByMemberIdAndType(101L, CAPITAL_PAYMENT))
        .thenReturn(new BigDecimal("987.65432"));
    when(memberCapitalEventRepository.getTotalOwnershipUnitsByMemberIdAndType(
            101L, CAPITAL_PAYMENT))
        .thenReturn(new BigDecimal("321.98765"));

    when(memberCapitalEventRepository.getTotalFiatValueByMemberIdAndType(101L, MEMBERSHIP_BONUS))
        .thenReturn(new BigDecimal("456.78901"));
    when(memberCapitalEventRepository.getTotalOwnershipUnitsByMemberIdAndType(
            101L, MEMBERSHIP_BONUS))
        .thenReturn(new BigDecimal("123.45678"));

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

    // CAPITAL_PAYMENT: (987.65432 * 71.17079) / 321.98765 = 218.30694
    BigDecimal expectedPaymentFiat = new BigDecimal("218.30694");

    // MEMBERSHIP_BONUS: 50.00 / 1.73456 = 28.82575 units (rounded to 5 decimal places)
    BigDecimal bonusUnits = new BigDecimal("28.82575");
    // (456.78901 * 28.82575) / 123.45678 = 106.65502
    BigDecimal expectedBonusFiat = new BigDecimal("106.65502");

    // Verify we have events for both types
    assertThat(savedEvents.stream().filter(e -> e.getType() == CAPITAL_PAYMENT).count())
        .isEqualTo(1);
    assertThat(savedEvents.stream().filter(e -> e.getType() == MEMBERSHIP_BONUS).count())
        .isEqualTo(1);
    assertThat(savedEvents.stream().filter(e -> e.getType() == CAPITAL_ACQUIRED).count())
        .isEqualTo(2);

    // Verify precise calculation results for CAPITAL_PAYMENT
    MemberCapitalEvent paymentSellerEvent =
        savedEvents.stream().filter(e -> e.getType() == CAPITAL_PAYMENT).findFirst().orElseThrow();
    assertThat(paymentSellerEvent.getFiatValue()).isEqualTo(expectedPaymentFiat.negate());
    assertThat(paymentSellerEvent.getOwnershipUnitAmount()).isEqualTo(EXPECTED_UNITS.negate());

    // Verify precise calculation results for MEMBERSHIP_BONUS
    MemberCapitalEvent bonusSellerEvent =
        savedEvents.stream().filter(e -> e.getType() == MEMBERSHIP_BONUS).findFirst().orElseThrow();
    assertThat(bonusSellerEvent.getFiatValue()).isEqualTo(expectedBonusFiat.negate());
    assertThat(bonusSellerEvent.getOwnershipUnitAmount()).isEqualTo(bonusUnits.negate());

    // Verify buyer events have matching fiat values to preserve total
    List<MemberCapitalEvent> buyerEvents =
        savedEvents.stream().filter(e -> e.getType() == CAPITAL_ACQUIRED).toList();
    assertThat(buyerEvents).hasSize(2);

    BigDecimal totalBuyerFiat =
        buyerEvents.stream()
            .map(MemberCapitalEvent::getFiatValue)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal totalSellerFiat = expectedPaymentFiat.add(expectedBonusFiat);
    assertThat(totalBuyerFiat).isEqualTo(totalSellerFiat);
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

    // Mock seller's total values for MEMBERSHIP_BONUS (only needed for valid amount)
    when(memberCapitalEventRepository.getTotalFiatValueByMemberIdAndType(101L, MEMBERSHIP_BONUS))
        .thenReturn(new BigDecimal("200.00000"));
    when(memberCapitalEventRepository.getTotalOwnershipUnitsByMemberIdAndType(
            101L, MEMBERSHIP_BONUS))
        .thenReturn(new BigDecimal("160.00000"));

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
