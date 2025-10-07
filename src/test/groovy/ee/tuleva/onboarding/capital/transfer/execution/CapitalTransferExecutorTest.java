package ee.tuleva.onboarding.capital.transfer.execution;

import static ee.tuleva.onboarding.auth.UserFixture.sampleUser;
import static ee.tuleva.onboarding.capital.event.member.MemberCapitalEventType.*;
import static ee.tuleva.onboarding.capital.transfer.CapitalTransferContractState.APPROVED_AND_NOTIFIED;
import static ee.tuleva.onboarding.mandate.email.persistence.EmailType.CAPITAL_TRANSFER_APPROVED_BY_BOARD;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import ee.tuleva.onboarding.capital.event.member.MemberCapitalEvent;
import ee.tuleva.onboarding.capital.event.member.MemberCapitalEventRepository;
import ee.tuleva.onboarding.capital.transfer.CapitalTransferContract;
import ee.tuleva.onboarding.capital.transfer.CapitalTransferContract.CapitalTransferAmount;
import ee.tuleva.onboarding.capital.transfer.CapitalTransferContractRepository;
import ee.tuleva.onboarding.capital.transfer.CapitalTransferContractService;
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
  @Mock private CapitalTransferValidator validator;
  @Mock private CapitalTransferEventLinkRepository linkRepository;
  @Mock private CapitalTransferContractService capitalTransferContractService;

  @Mock private CapitalTransferContract contract;
  @Mock private Member seller;
  @Mock private Member buyer;

  private CapitalTransferExecutor executor;

  private final BigDecimal OWNERSHIP_UNIT_PRICE = new BigDecimal("1.73456");
  private final BigDecimal BOOK_VALUE = new BigDecimal("123.45000");
  private final BigDecimal EXPECTED_UNITS =
      new BigDecimal("123.45000"); // 123.45 / 1.0 = 123.45 (uses stored ownership unit price)

  @BeforeEach
  public void setUp() {
    executor =
        new CapitalTransferExecutor(
            contractRepository,
            memberCapitalEventRepository,
            validator,
            capitalTransferContractService,
            linkRepository);
  }

  @Test
  @DisplayName("Should execute transfer successfully with single capital type")
  public void whenExecuteTransfer_withValidContract_thenSuccessfullyExecutes() {
    // Given
    when(contract.getId()).thenReturn(1L);
    when(contract.getSeller()).thenReturn(seller);
    when(contract.getBuyer()).thenReturn(buyer);
    when(seller.getUser()).thenReturn(sampleUser().id(1L).build());
    when(buyer.getUser()).thenReturn(sampleUser().id(2L).build());
    when(seller.getId()).thenReturn(101L);
    when(buyer.getId()).thenReturn(102L);

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
        new CapitalTransferAmount(
            CAPITAL_PAYMENT, new BigDecimal("125.00"), BOOK_VALUE, new BigDecimal("1.0"));
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
    // (987.65432 * 123.45) / 321.98765 = 378.66647 (using stored ownership unit price)
    BigDecimal expectedProportionalFiatValue = new BigDecimal("378.66647");

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

    verify(capitalTransferContractService, times(1))
        .sendContractEmail(
            eq(seller.getUser()), eq(CAPITAL_TRANSFER_APPROVED_BY_BOARD), eq(contract));
    verify(capitalTransferContractService, times(1))
        .sendContractEmail(
            eq(buyer.getUser()), eq(CAPITAL_TRANSFER_APPROVED_BY_BOARD), eq(contract));

    verify(capitalTransferContractService, times(1))
        .updateStateBySystem(contract.getId(), APPROVED_AND_NOTIFIED);
  }

  @Test
  @DisplayName("Should execute transfer with multiple capital types")
  public void whenExecuteTransfer_withMultipleTypes_thenAllTypesTransferred() {
    // Given
    when(contract.getId()).thenReturn(1L);
    when(contract.getSeller()).thenReturn(seller);
    when(contract.getBuyer()).thenReturn(buyer);
    when(seller.getUser()).thenReturn(sampleUser().id(1L).build());
    when(buyer.getUser()).thenReturn(sampleUser().id(2L).build());
    when(seller.getId()).thenReturn(101L);
    when(buyer.getId()).thenReturn(102L);

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
        new CapitalTransferAmount(
            CAPITAL_PAYMENT, new BigDecimal("125.00"), BOOK_VALUE, new BigDecimal("1.0"));
    CapitalTransferAmount bonus =
        new CapitalTransferAmount(
            MEMBERSHIP_BONUS,
            new BigDecimal("62.50"),
            new BigDecimal("50.00"),
            new BigDecimal("1.0"));
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

    // CAPITAL_PAYMENT: 123.45 / 1.0 = 123.45 units
    // (987.65432 * 123.45) / 321.98765 = 378.66647
    BigDecimal expectedPaymentFiat = new BigDecimal("378.66647");

    // MEMBERSHIP_BONUS: 50.00 / 1.0 = 50.00 units (using stored ownership unit price)
    BigDecimal bonusUnits = new BigDecimal("50.00000");
    // (456.78901 * 50.00) / 123.45678 = 184.99956
    BigDecimal expectedBonusFiat = new BigDecimal("184.99956");

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
    when(seller.getUser()).thenReturn(sampleUser().id(1L).build());
    when(buyer.getUser()).thenReturn(sampleUser().id(2L).build());

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
        new CapitalTransferAmount(
            CAPITAL_PAYMENT, new BigDecimal("100.00"), BigDecimal.ZERO, new BigDecimal("1.0"));
    CapitalTransferAmount validAmount =
        new CapitalTransferAmount(
            MEMBERSHIP_BONUS, new BigDecimal("125.00"), BOOK_VALUE, new BigDecimal("1.0"));
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
  @DisplayName("Should ensure fiatValue matches exactly between seller and buyer events")
  public void whenExecuteTransfer_thenFiatValueMatchesExactly() {
    // Given
    when(contract.getId()).thenReturn(1L);
    when(contract.getSeller()).thenReturn(seller);
    when(contract.getBuyer()).thenReturn(buyer);
    when(seller.getUser()).thenReturn(sampleUser().id(1L).build());
    when(buyer.getUser()).thenReturn(sampleUser().id(2L).build());
    when(seller.getId()).thenReturn(101L);
    when(buyer.getId()).thenReturn(102L);

    // Use values that would potentially cause rounding issues
    BigDecimal unitPrice = new BigDecimal("1.33333");
    BigDecimal bookValue = new BigDecimal("100.00000");

    // Seller's existing capital (values that could cause rounding issues)
    BigDecimal sellerTotalFiatValue = new BigDecimal("1000.12345");
    BigDecimal sellerTotalUnits = new BigDecimal("333.33333");
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
        new CapitalTransferAmount(
            CAPITAL_PAYMENT, new BigDecimal("100.00"), bookValue, new BigDecimal("1.0"));
    when(contract.getTransferAmounts()).thenReturn(List.of(transferAmount));

    // When
    executor.execute(contract);

    // Then
    ArgumentCaptor<MemberCapitalEvent> eventCaptor =
        ArgumentCaptor.forClass(MemberCapitalEvent.class);
    verify(memberCapitalEventRepository, times(2)).save(eventCaptor.capture());

    List<MemberCapitalEvent> savedEvents = eventCaptor.getAllValues();

    MemberCapitalEvent sellerEvent = savedEvents.get(0);
    MemberCapitalEvent buyerEvent = savedEvents.get(1);

    // The key assertion: seller's negative fiatValue should be exactly the negative of buyer's
    // positive fiatValue
    assertThat(sellerEvent.getFiatValue().negate()).isEqualTo(buyerEvent.getFiatValue());

    // Also verify ownership units match
    assertThat(sellerEvent.getOwnershipUnitAmount().negate())
        .isEqualTo(buyerEvent.getOwnershipUnitAmount());

    // Verify the events are for the correct members
    assertThat(sellerEvent.getMember()).isEqualTo(seller);
    assertThat(buyerEvent.getMember()).isEqualTo(buyer);
    assertThat(sellerEvent.getType()).isEqualTo(CAPITAL_PAYMENT);
    assertThat(buyerEvent.getType()).isEqualTo(CAPITAL_ACQUIRED);
  }

  @Test
  @DisplayName("Should clamp to total when transfer amount is within 2 cents of available total")
  public void whenExecuteTransfer_withAmountWithinTolerance_thenClampsToTotal() {
    // Given
    when(contract.getId()).thenReturn(1L);
    when(contract.getSeller()).thenReturn(seller);
    when(contract.getBuyer()).thenReturn(buyer);
    when(seller.getUser()).thenReturn(sampleUser().id(1L).build());
    when(buyer.getUser()).thenReturn(sampleUser().id(2L).build());
    when(seller.getId()).thenReturn(101L);
    when(buyer.getId()).thenReturn(102L);

    // Setup: seller has 100.00400 units available
    BigDecimal sellerTotalUnits = new BigDecimal("100.00400");
    BigDecimal sellerTotalFiatValue = new BigDecimal("200.00");
    BigDecimal ownershipUnitPrice = new BigDecimal("2.00");

    // Transfer requests 100.00000 units (book value 200.00)
    // Difference is 0.00400 units * 2.00 = 0.008 EUR (less than 0.02 tolerance)
    BigDecimal bookValue = new BigDecimal("200.00");

    when(memberCapitalEventRepository.getTotalOwnershipUnitsByMemberIdAndType(
            101L, CAPITAL_PAYMENT))
        .thenReturn(sellerTotalUnits);
    when(memberCapitalEventRepository.getTotalFiatValueByMemberIdAndType(101L, CAPITAL_PAYMENT))
        .thenReturn(sellerTotalFiatValue);

    when(memberCapitalEventRepository.save(any(MemberCapitalEvent.class)))
        .thenAnswer(
            invocation -> {
              MemberCapitalEvent event = invocation.getArgument(0);
              event.setId(System.nanoTime());
              return event;
            });

    CapitalTransferAmount transferAmount =
        new CapitalTransferAmount(
            CAPITAL_PAYMENT,
            new BigDecimal("250.00"), // price
            bookValue,
            ownershipUnitPrice);
    when(contract.getTransferAmounts()).thenReturn(List.of(transferAmount));

    // When
    executor.execute(contract);

    // Then
    ArgumentCaptor<MemberCapitalEvent> eventCaptor =
        ArgumentCaptor.forClass(MemberCapitalEvent.class);
    verify(memberCapitalEventRepository, times(2)).save(eventCaptor.capture());

    List<MemberCapitalEvent> savedEvents = eventCaptor.getAllValues();
    MemberCapitalEvent sellerEvent = savedEvents.get(0);

    // Verify that the TOTAL available units (100.00400) were used, not the calculated 100.00000
    assertThat(sellerEvent.getOwnershipUnitAmount()).isEqualTo(sellerTotalUnits.negate());

    // Verify the fiat value was calculated based on the total units
    // Expected: (200.00 * 100.00400) / 100.00400 = 200.00
    assertThat(sellerEvent.getFiatValue()).isEqualByComparingTo(sellerTotalFiatValue.negate());
  }

  @Test
  @DisplayName("Should not clamp when transfer amount differs by more than 2 cents from total")
  public void whenExecuteTransfer_withAmountExceedingTolerance_thenDoesNotClamp() {
    // Given
    when(contract.getId()).thenReturn(1L);
    when(contract.getSeller()).thenReturn(seller);
    when(contract.getBuyer()).thenReturn(buyer);
    when(seller.getUser()).thenReturn(sampleUser().id(1L).build());
    when(buyer.getUser()).thenReturn(sampleUser().id(2L).build());
    when(seller.getId()).thenReturn(101L);
    when(buyer.getId()).thenReturn(102L);

    // Setup: seller has 100.10000 units available
    BigDecimal sellerTotalUnits = new BigDecimal("100.10000");
    BigDecimal sellerTotalFiatValue = new BigDecimal("200.20");
    BigDecimal ownershipUnitPrice = new BigDecimal("2.00");

    // Transfer requests 100.00000 units (book value 200.00)
    // Difference is 0.10000 units * 2.00 = 0.20 EUR (exceeds 0.02 tolerance)
    BigDecimal bookValue = new BigDecimal("200.00");
    BigDecimal calculatedUnits = new BigDecimal("100.00000");

    when(memberCapitalEventRepository.getTotalOwnershipUnitsByMemberIdAndType(
            101L, CAPITAL_PAYMENT))
        .thenReturn(sellerTotalUnits);
    when(memberCapitalEventRepository.getTotalFiatValueByMemberIdAndType(101L, CAPITAL_PAYMENT))
        .thenReturn(sellerTotalFiatValue);

    when(memberCapitalEventRepository.save(any(MemberCapitalEvent.class)))
        .thenAnswer(
            invocation -> {
              MemberCapitalEvent event = invocation.getArgument(0);
              event.setId(System.nanoTime());
              return event;
            });

    CapitalTransferAmount transferAmount =
        new CapitalTransferAmount(
            CAPITAL_PAYMENT,
            new BigDecimal("250.00"), // price
            bookValue,
            ownershipUnitPrice);
    when(contract.getTransferAmounts()).thenReturn(List.of(transferAmount));

    // When
    executor.execute(contract);

    // Then
    ArgumentCaptor<MemberCapitalEvent> eventCaptor =
        ArgumentCaptor.forClass(MemberCapitalEvent.class);
    verify(memberCapitalEventRepository, times(2)).save(eventCaptor.capture());

    List<MemberCapitalEvent> savedEvents = eventCaptor.getAllValues();
    MemberCapitalEvent sellerEvent = savedEvents.get(0);

    // Verify that the CALCULATED units (100.00000) were used, NOT the total (100.10000)
    assertThat(sellerEvent.getOwnershipUnitAmount()).isEqualTo(calculatedUnits.negate());

    // Verify the fiat value was calculated based on the calculated units
    // Expected: (200.20 * 100.00000) / 100.10000 = 200.00000 (rounded)
    BigDecimal expectedFiatValue = new BigDecimal("200.00000");
    assertThat(sellerEvent.getFiatValue()).isEqualTo(expectedFiatValue.negate());
  }

  @Test
  @DisplayName("Should fail when transfer request significantly exceeds available capital")
  public void whenExecuteTransfer_withLargeOverdraft_thenThrowsException() {
    // Given
    when(contract.getSeller()).thenReturn(seller);
    when(seller.getId()).thenReturn(101L);

    // Setup: seller has only 95.00000 units available
    BigDecimal sellerTotalUnits = new BigDecimal("95.00000");
    BigDecimal ownershipUnitPrice = new BigDecimal("2.00");

    // Transfer requests 100.00000 units (book value 200.00)
    // Overdraft is 5.00000 units * 2.00 = 10.00 EUR (exceeds 0.02 EUR tolerance)
    BigDecimal bookValue = new BigDecimal("200.00");

    when(memberCapitalEventRepository.getTotalOwnershipUnitsByMemberIdAndType(
            101L, CAPITAL_PAYMENT))
        .thenReturn(sellerTotalUnits);

    CapitalTransferAmount transferAmount =
        new CapitalTransferAmount(
            CAPITAL_PAYMENT,
            new BigDecimal("250.00"), // price
            bookValue,
            ownershipUnitPrice);
    when(contract.getTransferAmounts()).thenReturn(List.of(transferAmount));

    // When & Then - Should throw exception for large overdraft
    assertThatThrownBy(() -> executor.execute(contract))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Transfer request exceeds clamping tolerance of available capital")
        .hasMessageContaining("Overdraft: 10.00000 EUR")
        .hasMessageContaining("error in application creation");
  }

  @Test
  @DisplayName("Should clamp when transfer request slightly exceeds available capital")
  public void whenExecuteTransfer_withSmallOverdraft_thenClampsToAvailable() {
    // Given
    when(contract.getId()).thenReturn(1L);
    when(contract.getSeller()).thenReturn(seller);
    when(contract.getBuyer()).thenReturn(buyer);
    when(seller.getUser()).thenReturn(sampleUser().id(1L).build());
    when(buyer.getUser()).thenReturn(sampleUser().id(2L).build());
    when(seller.getId()).thenReturn(101L);
    when(buyer.getId()).thenReturn(102L);

    // Setup: seller has 99.99000 units available
    BigDecimal sellerTotalUnits = new BigDecimal("99.99000");
    BigDecimal sellerTotalFiatValue = new BigDecimal("199.98");
    BigDecimal ownershipUnitPrice = new BigDecimal("2.00");

    // Transfer requests 100.00000 units (book value 200.00)
    // Overdraft is 0.01000 units * 2.00 = 0.02 EUR (exactly at 0.02 tolerance)
    BigDecimal bookValue = new BigDecimal("200.00");

    when(memberCapitalEventRepository.getTotalOwnershipUnitsByMemberIdAndType(
            101L, CAPITAL_PAYMENT))
        .thenReturn(sellerTotalUnits);
    when(memberCapitalEventRepository.getTotalFiatValueByMemberIdAndType(101L, CAPITAL_PAYMENT))
        .thenReturn(sellerTotalFiatValue);

    when(memberCapitalEventRepository.save(any(MemberCapitalEvent.class)))
        .thenAnswer(
            invocation -> {
              MemberCapitalEvent event = invocation.getArgument(0);
              event.setId(System.nanoTime());
              return event;
            });

    CapitalTransferAmount transferAmount =
        new CapitalTransferAmount(
            CAPITAL_PAYMENT,
            new BigDecimal("250.00"), // price
            bookValue,
            ownershipUnitPrice);
    when(contract.getTransferAmounts()).thenReturn(List.of(transferAmount));

    // When
    executor.execute(contract);

    // Then
    ArgumentCaptor<MemberCapitalEvent> eventCaptor =
        ArgumentCaptor.forClass(MemberCapitalEvent.class);
    verify(memberCapitalEventRepository, times(2)).save(eventCaptor.capture());

    List<MemberCapitalEvent> savedEvents = eventCaptor.getAllValues();
    MemberCapitalEvent sellerEvent = savedEvents.get(0);

    // Verify that the AVAILABLE units (99.99000) were used, not the requested (100.00000)
    assertThat(sellerEvent.getOwnershipUnitAmount()).isEqualTo(sellerTotalUnits.negate());

    // Verify the fiat value was calculated based on the available units
    assertThat(sellerEvent.getFiatValue()).isEqualByComparingTo(sellerTotalFiatValue.negate());
  }
}
