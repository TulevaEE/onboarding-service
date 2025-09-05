package ee.tuleva.onboarding.capital.transfer.execution;

import static ee.tuleva.onboarding.capital.event.member.MemberCapitalEventType.*;
import static ee.tuleva.onboarding.capital.transfer.CapitalTransferContractState.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import ee.tuleva.onboarding.capital.event.member.MemberCapitalEvent;
import ee.tuleva.onboarding.capital.event.member.MemberCapitalEventRepository;
import ee.tuleva.onboarding.capital.transfer.CapitalTransferContract;
import ee.tuleva.onboarding.capital.transfer.CapitalTransferContract.CapitalTransferAmount;
import ee.tuleva.onboarding.user.member.Member;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class CapitalTransferValidatorTest {

  @Mock private MemberCapitalEventRepository memberCapitalEventRepository;
  @Mock private CapitalTransferContract contract;
  @Mock private Member seller;

  private CapitalTransferValidator validator;

  @BeforeEach
  public void setUp() {
    validator = new CapitalTransferValidator(memberCapitalEventRepository);
  }

  @Test
  @DisplayName("Should validate approved contract successfully")
  public void whenValidateContract_withApprovedState_thenSucceeds() {
    // Given
    when(contract.getState()).thenReturn(APPROVED);

    // When & Then - Should not throw exception
    validator.validateContract(contract);
  }

  @Test
  @DisplayName("Should throw exception for null contract")
  public void whenValidateContract_withNullContract_thenThrowsException() {
    // When & Then
    assertThatThrownBy(() -> validator.validateContract(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Capital transfer contract not found");
  }

  @Test
  @DisplayName("Should throw exception for non-approved contract")
  public void whenValidateContract_withCreatedState_thenThrowsException() {
    // Given
    when(contract.getId()).thenReturn(1L);
    when(contract.getState()).thenReturn(CREATED);

    // When & Then
    assertThatThrownBy(() -> validator.validateContract(contract))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage(
            "Capital transfer contract 1 is not in APPROVED state (current state: CREATED)");
  }

  @Test
  @DisplayName("Should validate sufficient capital successfully")
  public void whenValidateSufficientCapital_withSufficientAmount_thenSucceeds() {
    // Given
    when(contract.getSeller()).thenReturn(seller);
    when(seller.getId()).thenReturn(1L);
    CapitalTransferAmount transferAmount =
        new CapitalTransferAmount(
            CAPITAL_PAYMENT, new BigDecimal("125.00"), new BigDecimal("100.00"));
    when(contract.getTransferAmounts()).thenReturn(List.of(transferAmount));

    // Mock seller has 150.00 of CAPITAL_PAYMENT
    MemberCapitalEvent event = createMemberCapitalEvent(CAPITAL_PAYMENT, new BigDecimal("150.00"));
    when(memberCapitalEventRepository.findAllByMemberId(1L)).thenReturn(List.of(event));

    // When & Then - Should not throw exception
    validator.validateSufficientCapital(contract);
  }

  @Test
  @DisplayName("Should throw exception for insufficient capital")
  public void whenValidateSufficientCapital_withInsufficientAmount_thenThrowsException() {
    // Given
    when(contract.getSeller()).thenReturn(seller);
    when(seller.getId()).thenReturn(1L);
    CapitalTransferAmount transferAmount =
        new CapitalTransferAmount(
            CAPITAL_PAYMENT, new BigDecimal("125.00"), new BigDecimal("100.00"));
    when(contract.getTransferAmounts()).thenReturn(List.of(transferAmount));

    // Mock seller has only 50.00 of CAPITAL_PAYMENT
    MemberCapitalEvent event = createMemberCapitalEvent(CAPITAL_PAYMENT, new BigDecimal("50.00"));
    when(memberCapitalEventRepository.findAllByMemberId(1L)).thenReturn(List.of(event));

    // When & Then
    assertThatThrownBy(() -> validator.validateSufficientCapital(contract))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage(
            "Seller has insufficient CAPITAL_PAYMENT capital. Available: 50.00, Required: 100.00");
  }

  @Test
  @DisplayName("Should handle multiple capital types correctly")
  public void whenValidateSufficientCapital_withMultipleTypes_thenValidatesEachType() {
    // Given
    when(contract.getSeller()).thenReturn(seller);
    when(seller.getId()).thenReturn(1L);
    CapitalTransferAmount payment =
        new CapitalTransferAmount(
            CAPITAL_PAYMENT, new BigDecimal("125.00"), new BigDecimal("100.00"));
    CapitalTransferAmount bonus =
        new CapitalTransferAmount(
            MEMBERSHIP_BONUS, new BigDecimal("25.00"), new BigDecimal("20.00"));
    when(contract.getTransferAmounts()).thenReturn(List.of(payment, bonus));

    // Mock seller has sufficient capital for both types
    MemberCapitalEvent paymentEvent =
        createMemberCapitalEvent(CAPITAL_PAYMENT, new BigDecimal("150.00"));
    MemberCapitalEvent bonusEvent =
        createMemberCapitalEvent(MEMBERSHIP_BONUS, new BigDecimal("30.00"));
    when(memberCapitalEventRepository.findAllByMemberId(1L))
        .thenReturn(List.of(paymentEvent, bonusEvent));

    // When & Then - Should not throw exception
    validator.validateSufficientCapital(contract);
  }

  @Test
  @DisplayName("Should throw exception when one type is insufficient in multi-type transfer")
  public void whenValidateSufficientCapital_withMultipleTypesOneInsufficient_thenThrowsException() {
    // Given
    when(contract.getSeller()).thenReturn(seller);
    when(seller.getId()).thenReturn(1L);
    CapitalTransferAmount payment =
        new CapitalTransferAmount(
            CAPITAL_PAYMENT, new BigDecimal("125.00"), new BigDecimal("100.00"));
    CapitalTransferAmount bonus =
        new CapitalTransferAmount(
            MEMBERSHIP_BONUS, new BigDecimal("25.00"), new BigDecimal("20.00"));
    when(contract.getTransferAmounts()).thenReturn(List.of(payment, bonus));

    // Mock seller has sufficient CAPITAL_PAYMENT but insufficient MEMBERSHIP_BONUS
    MemberCapitalEvent paymentEvent =
        createMemberCapitalEvent(CAPITAL_PAYMENT, new BigDecimal("150.00"));
    MemberCapitalEvent bonusEvent =
        createMemberCapitalEvent(MEMBERSHIP_BONUS, new BigDecimal("10.00"));
    when(memberCapitalEventRepository.findAllByMemberId(1L))
        .thenReturn(List.of(paymentEvent, bonusEvent));

    // When & Then
    assertThatThrownBy(() -> validator.validateSufficientCapital(contract))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage(
            "Seller has insufficient MEMBERSHIP_BONUS capital. Available: 10.00, Required: 20.00");
  }

  @Test
  @DisplayName("Should aggregate capital events by type correctly")
  public void whenValidateSufficientCapital_withMultipleEventsOfSameType_thenAggregatesCorrectly() {
    // Given
    when(contract.getSeller()).thenReturn(seller);
    when(seller.getId()).thenReturn(1L);
    CapitalTransferAmount transferAmount =
        new CapitalTransferAmount(
            CAPITAL_PAYMENT, new BigDecimal("125.00"), new BigDecimal("100.00"));
    when(contract.getTransferAmounts()).thenReturn(List.of(transferAmount));

    // Mock seller has multiple events of same type that sum to sufficient amount
    MemberCapitalEvent event1 = createMemberCapitalEvent(CAPITAL_PAYMENT, new BigDecimal("60.00"));
    MemberCapitalEvent event2 = createMemberCapitalEvent(CAPITAL_PAYMENT, new BigDecimal("50.00"));
    when(memberCapitalEventRepository.findAllByMemberId(1L)).thenReturn(List.of(event1, event2));

    // When & Then - Should not throw exception (60 + 50 = 110 > 100 required)
    validator.validateSufficientCapital(contract);
  }

  @Test
  @DisplayName("Should handle negative capital events correctly")
  public void whenValidateSufficientCapital_withNegativeEvents_thenCalculatesNetAmount() {
    // Given
    when(contract.getSeller()).thenReturn(seller);
    when(seller.getId()).thenReturn(1L);
    CapitalTransferAmount transferAmount =
        new CapitalTransferAmount(
            CAPITAL_PAYMENT, new BigDecimal("125.00"), new BigDecimal("100.00"));
    when(contract.getTransferAmounts()).thenReturn(List.of(transferAmount));

    // Mock seller has positive and negative events (e.g., from previous transfers)
    MemberCapitalEvent positiveEvent =
        createMemberCapitalEvent(CAPITAL_PAYMENT, new BigDecimal("200.00"));
    MemberCapitalEvent negativeEvent =
        createMemberCapitalEvent(CAPITAL_PAYMENT, new BigDecimal("-50.00"));
    when(memberCapitalEventRepository.findAllByMemberId(1L))
        .thenReturn(List.of(positiveEvent, negativeEvent));

    // When & Then - Should not throw exception (200 - 50 = 150 > 100 required)
    validator.validateSufficientCapital(contract);
  }

  @Test
  @DisplayName("Should return true for zero book value")
  public void whenShouldSkipTransfer_withZeroBookValue_thenReturnsTrue() {
    // Given
    CapitalTransferAmount transferAmount =
        new CapitalTransferAmount(CAPITAL_PAYMENT, new BigDecimal("100.00"), BigDecimal.ZERO);

    // When
    boolean result = validator.shouldSkipTransfer(transferAmount);

    // Then
    assertThat(result).isTrue();
  }

  @Test
  @DisplayName("Should return true for null book value")
  public void whenShouldSkipTransfer_withNullBookValue_thenReturnsTrue() {
    // Given
    CapitalTransferAmount transferAmount =
        new CapitalTransferAmount(CAPITAL_PAYMENT, new BigDecimal("100.00"), null);

    // When
    boolean result = validator.shouldSkipTransfer(transferAmount);

    // Then
    assertThat(result).isTrue();
  }

  @Test
  @DisplayName("Should return false for positive book value")
  public void whenShouldSkipTransfer_withPositiveBookValue_thenReturnsFalse() {
    // Given
    CapitalTransferAmount transferAmount =
        new CapitalTransferAmount(
            CAPITAL_PAYMENT, new BigDecimal("100.00"), new BigDecimal("50.00"));

    // When
    boolean result = validator.shouldSkipTransfer(transferAmount);

    // Then
    assertThat(result).isFalse();
  }

  @Test
  @DisplayName("Should skip zero and null book value transfers")
  public void whenValidateSufficientCapital_withZeroAndNullBookValues_thenSkipsThem() {
    // Given
    when(contract.getSeller()).thenReturn(seller);
    when(seller.getId()).thenReturn(1L);
    CapitalTransferAmount zeroAmount =
        new CapitalTransferAmount(CAPITAL_PAYMENT, new BigDecimal("100.00"), BigDecimal.ZERO);
    CapitalTransferAmount nullAmount =
        new CapitalTransferAmount(MEMBERSHIP_BONUS, new BigDecimal("100.00"), null);
    CapitalTransferAmount validAmount =
        new CapitalTransferAmount(
            WORK_COMPENSATION, new BigDecimal("100.00"), new BigDecimal("50.00"));
    when(contract.getTransferAmounts()).thenReturn(List.of(zeroAmount, nullAmount, validAmount));

    // Mock seller has sufficient capital for the valid amount only
    MemberCapitalEvent event =
        createMemberCapitalEvent(WORK_COMPENSATION, new BigDecimal("100.00"));
    when(memberCapitalEventRepository.findAllByMemberId(1L)).thenReturn(List.of(event));

    // When & Then - Should not throw exception, zero and null amounts should be skipped
    validator.validateSufficientCapital(contract);
  }

  private MemberCapitalEvent createMemberCapitalEvent(
      ee.tuleva.onboarding.capital.event.member.MemberCapitalEventType type, BigDecimal fiatValue) {
    return MemberCapitalEvent.builder()
        .member(seller)
        .type(type)
        .fiatValue(fiatValue)
        .ownershipUnitAmount(fiatValue)
        .accountingDate(LocalDate.now(ZoneId.of("Europe/Tallinn")))
        .effectiveDate(LocalDate.now(ZoneId.of("Europe/Tallinn")))
        .build();
  }
}
