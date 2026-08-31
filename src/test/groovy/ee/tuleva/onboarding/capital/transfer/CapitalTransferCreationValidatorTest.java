package ee.tuleva.onboarding.capital.transfer;

import static ee.tuleva.onboarding.auth.UserFixture.sampleUser;
import static ee.tuleva.onboarding.capital.event.member.MemberCapitalEventType.*;
import static ee.tuleva.onboarding.capital.transfer.CapitalTransferContractState.*;
import static ee.tuleva.onboarding.user.MemberFixture.memberFixture;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ee.tuleva.onboarding.capital.CapitalRow;
import ee.tuleva.onboarding.capital.CapitalService;
import ee.tuleva.onboarding.capital.transfer.CapitalTransferContract.CapitalTransferAmount;
import ee.tuleva.onboarding.currency.Currency;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CapitalTransferCreationValidatorTest {

  @Mock private CapitalTransferContractRepository contractRepository;
  @Mock private CapitalService capitalService;

  private CapitalTransferCreationValidator validator;

  @BeforeEach
  void setUp() {
    validator =
        new CapitalTransferCreationValidator(
            capitalService, new ActiveTransferCapital(contractRepository));
  }

  @Test
  void validateDoesNotThrowWhenSellerHasEnoughCapitalAcrossOtherTransfers() {
    var buyerUser = sampleUser().firstName("Olev").lastName("Ostja").build();
    var sellerUser = sampleUser().member(memberFixture().id(2L).build()).build();
    var seller = sellerUser.getMemberOrThrow();
    var buyer = memberFixture().id(3L).user(buyerUser).build();

    var command =
        CreateCapitalTransferContractCommand.builder()
            .buyerMemberId(3L)
            .iban("TEST_IBAN")
            .transferAmounts(
                List.of(
                    new CapitalTransferAmount(
                        CAPITAL_PAYMENT,
                        new BigDecimal("100.0"),
                        new BigDecimal("100.0"),
                        new BigDecimal("1.0"))))
            .build();

    when(contractRepository.findAllBySellerId(seller.getId()))
        .thenReturn(
            List.of(
                CapitalTransferContract.builder()
                    .id(1L)
                    .state(SELLER_SIGNED)
                    .seller(seller)
                    .buyer(memberFixture().id(3L).build())
                    .transferAmounts(
                        List.of(
                            new CapitalTransferAmount(
                                CAPITAL_PAYMENT,
                                new BigDecimal("40.0"),
                                new BigDecimal("450.0"),
                                new BigDecimal("1.0"))))
                    .build(),
                CapitalTransferContract.builder()
                    .id(2L)
                    .state(SELLER_SIGNED)
                    .seller(seller)
                    .buyer(memberFixture().id(3L).build())
                    .transferAmounts(
                        List.of(
                            new CapitalTransferAmount(
                                CAPITAL_PAYMENT,
                                new BigDecimal("40.0"),
                                new BigDecimal("450.0"),
                                new BigDecimal("1.0"))))
                    .build()));

    when(capitalService.getCapitalRows(seller.getId()))
        .thenReturn(
            List.of(
                new CapitalRow(
                    CAPITAL_PAYMENT,
                    BigDecimal.valueOf(100),
                    BigDecimal.valueOf(900),
                    BigDecimal.valueOf(100),
                    BigDecimal.valueOf(10),
                    Currency.EUR),
                new CapitalRow(
                    MEMBERSHIP_BONUS,
                    BigDecimal.valueOf(0.5),
                    BigDecimal.valueOf(4.5),
                    BigDecimal.valueOf(0.5),
                    BigDecimal.valueOf(10),
                    Currency.EUR)));

    when(capitalService.getCapitalConcentrationUnitLimit()).thenReturn(BigDecimal.valueOf(1e8));

    assertDoesNotThrow(() -> validator.validate(seller, buyer, command));
  }

  @Test
  void validateThrowsWhenCapitalAlreadyBeingSoldInOtherTransfers() {
    var sellerUser = sampleUser().member(memberFixture().id(2L).build()).build();
    var seller = sellerUser.getMemberOrThrow();
    var buyer = memberFixture().id(3L).build();

    var command =
        CreateCapitalTransferContractCommand.builder()
            .buyerMemberId(3L)
            .iban("TEST_IBAN")
            .transferAmounts(
                List.of(
                    new CapitalTransferAmount(
                        CAPITAL_PAYMENT,
                        new BigDecimal("100.0"),
                        new BigDecimal("10.0"),
                        new BigDecimal("1.0"))))
            .build();

    when(contractRepository.findAllBySellerId(seller.getId()))
        .thenReturn(
            List.of(
                CapitalTransferContract.builder()
                    .id(1L)
                    .state(SELLER_SIGNED)
                    .seller(seller)
                    .buyer(memberFixture().id(3L).build())
                    .transferAmounts(
                        List.of(
                            new CapitalTransferAmount(
                                CAPITAL_PAYMENT,
                                new BigDecimal("50.0"),
                                new BigDecimal("100.0"),
                                new BigDecimal("1.0"))))
                    .build(),
                CapitalTransferContract.builder()
                    .id(2L)
                    .state(SELLER_SIGNED)
                    .seller(seller)
                    .buyer(memberFixture().id(3L).build())
                    .transferAmounts(
                        List.of(
                            new CapitalTransferAmount(
                                CAPITAL_PAYMENT,
                                new BigDecimal("50.0"),
                                new BigDecimal("100.0"),
                                new BigDecimal("1.0"))))
                    .build()));

    when(capitalService.getCapitalRows(seller.getId()))
        .thenReturn(
            List.of(
                new CapitalRow(
                    CAPITAL_PAYMENT,
                    BigDecimal.valueOf(10),
                    BigDecimal.valueOf(90),
                    BigDecimal.valueOf(10),
                    BigDecimal.valueOf(10),
                    Currency.EUR)));

    IllegalStateException thrown =
        assertThrows(IllegalStateException.class, () -> validator.validate(seller, buyer, command));
    assertEquals("Seller does not have enough member capital", thrown.getMessage());
  }

  @Test
  void validateThrowsWhenSellerDoesNotHaveEnoughCapital() {
    var sellerUser = sampleUser().member(memberFixture().id(2L).build()).build();
    var seller = sellerUser.getMemberOrThrow();
    var buyer = memberFixture().id(3L).build();

    var command =
        CreateCapitalTransferContractCommand.builder()
            .buyerMemberId(3L)
            .iban("TEST_IBAN")
            .transferAmounts(
                List.of(
                    new CapitalTransferAmount(
                        CAPITAL_PAYMENT,
                        new BigDecimal("100.0"),
                        new BigDecimal("10.0"),
                        new BigDecimal("1.0"))))
            .build();

    when(contractRepository.findAllBySellerId(seller.getId())).thenReturn(List.of());

    when(capitalService.getCapitalRows(seller.getId()))
        .thenReturn(
            List.of(
                new CapitalRow(
                    CAPITAL_PAYMENT,
                    BigDecimal.valueOf(0.1),
                    BigDecimal.valueOf(0.9),
                    BigDecimal.valueOf(0.5),
                    BigDecimal.valueOf(10),
                    Currency.EUR)));

    IllegalStateException thrown =
        assertThrows(IllegalStateException.class, () -> validator.validate(seller, buyer, command));
    assertEquals("Seller does not have enough member capital", thrown.getMessage());
  }

  @Test
  void validateThrowsWhenSomeCapitalAvailableButOthersAlreadyBeingSoldInOtherTransfers() {
    var sellerUser = sampleUser().member(memberFixture().id(2L).build()).build();
    var seller = sellerUser.getMemberOrThrow();
    var buyer = memberFixture().id(3L).build();

    var command =
        CreateCapitalTransferContractCommand.builder()
            .buyerMemberId(3L)
            .iban("TEST_IBAN")
            .transferAmounts(
                List.of(
                    new CapitalTransferAmount(
                        CAPITAL_PAYMENT,
                        new BigDecimal("100.0"),
                        new BigDecimal("10.0"),
                        new BigDecimal("1.0")),
                    new CapitalTransferAmount(
                        MEMBERSHIP_BONUS,
                        new BigDecimal("100.0"),
                        new BigDecimal("5.0"),
                        new BigDecimal("1.0"))))
            .build();

    when(contractRepository.findAllBySellerId(seller.getId()))
        .thenReturn(
            List.of(
                CapitalTransferContract.builder()
                    .id(1L)
                    .state(SELLER_SIGNED)
                    .seller(seller)
                    .buyer(memberFixture().id(3L).build())
                    .transferAmounts(
                        List.of(
                            new CapitalTransferAmount(
                                CAPITAL_PAYMENT,
                                new BigDecimal("50.0"),
                                new BigDecimal("100.0"),
                                new BigDecimal("1.0"))))
                    .build(),
                CapitalTransferContract.builder()
                    .id(2L)
                    .state(SELLER_SIGNED)
                    .seller(seller)
                    .buyer(memberFixture().id(3L).build())
                    .transferAmounts(
                        List.of(
                            new CapitalTransferAmount(
                                MEMBERSHIP_BONUS,
                                new BigDecimal("50.0"),
                                new BigDecimal("5.0"),
                                new BigDecimal("1.0"))))
                    .build()));

    when(capitalService.getCapitalRows(seller.getId()))
        .thenReturn(
            List.of(
                new CapitalRow(
                    CAPITAL_PAYMENT,
                    BigDecimal.valueOf(10),
                    BigDecimal.valueOf(90),
                    BigDecimal.valueOf(10),
                    BigDecimal.valueOf(10),
                    Currency.EUR),
                new CapitalRow(
                    MEMBERSHIP_BONUS,
                    BigDecimal.valueOf(0.5),
                    BigDecimal.valueOf(4.5),
                    BigDecimal.valueOf(0.5),
                    BigDecimal.valueOf(10),
                    Currency.EUR)));

    IllegalStateException thrown =
        assertThrows(IllegalStateException.class, () -> validator.validate(seller, buyer, command));
    assertEquals("Seller does not have enough member capital", thrown.getMessage());
  }

  @Test
  void validateThrowsWhenOnlyTheBookValueIsZero() {
    var sellerUser = sampleUser().member(memberFixture().id(2L).build()).build();
    var seller = sellerUser.getMemberOrThrow();
    var buyer = memberFixture().id(3L).build();

    var command =
        CreateCapitalTransferContractCommand.builder()
            .buyerMemberId(3L)
            .iban("TEST_IBAN")
            .transferAmounts(
                List.of(
                    new CapitalTransferAmount(
                        CAPITAL_PAYMENT,
                        new BigDecimal("100.0"),
                        new BigDecimal("0.0"),
                        new BigDecimal("1.0"))))
            .build();

    assertThatThrownBy(() -> validator.validate(seller, buyer, command))
        .isInstanceOf(IllegalArgumentException.class);
    verifyNoInteractions(capitalService);
  }

  @Test
  void validateThrowsWhenOnlyThePriceIsZero() {
    var sellerUser = sampleUser().member(memberFixture().id(2L).build()).build();
    var seller = sellerUser.getMemberOrThrow();
    var buyer = memberFixture().id(3L).build();

    var command =
        CreateCapitalTransferContractCommand.builder()
            .buyerMemberId(3L)
            .iban("TEST_IBAN")
            .transferAmounts(
                List.of(
                    new CapitalTransferAmount(
                        CAPITAL_PAYMENT,
                        new BigDecimal("0.0"),
                        new BigDecimal("200.0"),
                        new BigDecimal("1.0"))))
            .build();

    assertThatThrownBy(() -> validator.validate(seller, buyer, command))
        .isInstanceOf(IllegalArgumentException.class);
    verifyNoInteractions(capitalService);
  }

  @Test
  void validateThrowsWhenAmountsAreZero() {
    var sellerUser = sampleUser().member(memberFixture().id(2L).build()).build();
    var seller = sellerUser.getMemberOrThrow();
    var buyer = memberFixture().id(3L).build();

    var command =
        CreateCapitalTransferContractCommand.builder()
            .buyerMemberId(3L)
            .iban("TEST_IBAN")
            .transferAmounts(
                List.of(
                    new CapitalTransferAmount(
                        CAPITAL_PAYMENT,
                        new BigDecimal("100.0"),
                        new BigDecimal("200.0"),
                        new BigDecimal("1.0")),
                    new CapitalTransferAmount(
                        MEMBERSHIP_BONUS,
                        new BigDecimal("0.0"),
                        new BigDecimal("0.0"),
                        new BigDecimal("1.0"))))
            .build();

    IllegalArgumentException thrown =
        assertThrows(
            IllegalArgumentException.class, () -> validator.validate(seller, buyer, command));
    assertEquals("Amounts or prices have negative or zero values", thrown.getMessage());
  }

  @Test
  void validateThrowsWhenAmountsAreNegative() {
    var sellerUser = sampleUser().member(memberFixture().id(2L).build()).build();
    var seller = sellerUser.getMemberOrThrow();
    var buyer = memberFixture().id(3L).build();

    var command =
        CreateCapitalTransferContractCommand.builder()
            .buyerMemberId(3L)
            .iban("TEST_IBAN")
            .transferAmounts(
                List.of(
                    new CapitalTransferAmount(
                        CAPITAL_PAYMENT,
                        new BigDecimal("100.0"),
                        new BigDecimal("200.0"),
                        new BigDecimal("1.0")),
                    new CapitalTransferAmount(
                        MEMBERSHIP_BONUS,
                        new BigDecimal("-30.0"),
                        new BigDecimal("-20.0"),
                        new BigDecimal("1.0"))))
            .build();

    IllegalArgumentException thrown =
        assertThrows(
            IllegalArgumentException.class, () -> validator.validate(seller, buyer, command));
    assertEquals("Amounts or prices have negative or zero values", thrown.getMessage());
  }

  @Test
  void validateThrowsWhenNotEnoughMemberBonus() {
    var sellerUser = sampleUser().member(memberFixture().id(2L).build()).build();
    var seller = sellerUser.getMemberOrThrow();
    var buyer = memberFixture().id(3L).build();

    var command =
        CreateCapitalTransferContractCommand.builder()
            .buyerMemberId(3L)
            .iban("TEST_IBAN")
            .transferAmounts(
                List.of(
                    new CapitalTransferAmount(
                        MEMBERSHIP_BONUS,
                        new BigDecimal("100.0"),
                        new BigDecimal("10.0"),
                        new BigDecimal("1.0"))))
            .build();

    when(contractRepository.findAllBySellerId(seller.getId())).thenReturn(List.of());

    when(capitalService.getCapitalRows(seller.getId()))
        .thenReturn(
            List.of(
                new CapitalRow(
                    CAPITAL_PAYMENT,
                    BigDecimal.valueOf(100),
                    BigDecimal.valueOf(900),
                    BigDecimal.valueOf(100),
                    BigDecimal.valueOf(10),
                    Currency.EUR),
                new CapitalRow(
                    MEMBERSHIP_BONUS,
                    BigDecimal.valueOf(0.5),
                    BigDecimal.valueOf(4.5),
                    BigDecimal.valueOf(0.5),
                    BigDecimal.valueOf(10),
                    Currency.EUR)));

    IllegalStateException thrown =
        assertThrows(IllegalStateException.class, () -> validator.validate(seller, buyer, command));
    assertEquals("Seller does not have enough member capital", thrown.getMessage());
  }

  @Test
  void validateThrowsWhenExceedingConcentrationLimit() {
    var buyerUser = sampleUser().firstName("Olev").lastName("Ostja").build();
    var sellerUser = sampleUser().member(memberFixture().id(2L).build()).build();
    var seller = sellerUser.getMemberOrThrow();
    var buyer = memberFixture().id(3L).user(buyerUser).build();

    var command =
        CreateCapitalTransferContractCommand.builder()
            .buyerMemberId(3L)
            .iban("TEST_IBAN")
            .transferAmounts(
                List.of(
                    new CapitalTransferAmount(
                        CAPITAL_PAYMENT,
                        new BigDecimal("100.0"),
                        new BigDecimal("10.0"),
                        new BigDecimal("1.0"))))
            .build();

    when(contractRepository.findAllBySellerId(seller.getId())).thenReturn(List.of());

    when(capitalService.getCapitalRows(seller.getId()))
        .thenReturn(
            List.of(
                new CapitalRow(
                    CAPITAL_PAYMENT,
                    BigDecimal.valueOf(100),
                    BigDecimal.valueOf(900),
                    BigDecimal.valueOf(100),
                    BigDecimal.valueOf(10),
                    Currency.EUR),
                new CapitalRow(
                    MEMBERSHIP_BONUS,
                    BigDecimal.valueOf(0.5),
                    BigDecimal.valueOf(4.5),
                    BigDecimal.valueOf(0.5),
                    BigDecimal.valueOf(10),
                    Currency.EUR)));
    when(capitalService.getCapitalConcentrationUnitLimit()).thenReturn(BigDecimal.valueOf(10));

    IllegalStateException thrown =
        assertThrows(IllegalStateException.class, () -> validator.validate(seller, buyer, command));
    assertEquals("Buyer would exceed concentration limit after transfer", thrown.getMessage());
  }

  @Test
  void validateThrowsWhenExceedingConcentrationLimitWithOtherTransfers() {
    var buyerUser =
        sampleUser()
            .member(memberFixture().id(3L).build())
            .firstName("Olev")
            .lastName("Ostja")
            .build();
    var sellerUser = sampleUser().member(memberFixture().id(2L).build()).build();
    var seller = sellerUser.getMemberOrThrow();
    var buyer = memberFixture().id(3L).user(buyerUser).build();

    var command =
        CreateCapitalTransferContractCommand.builder()
            .buyerMemberId(3L)
            .iban("TEST_IBAN")
            .transferAmounts(
                List.of(
                    new CapitalTransferAmount(
                        CAPITAL_PAYMENT,
                        new BigDecimal("100.0"),
                        new BigDecimal("90.0"),
                        new BigDecimal("1.0"))))
            .build();

    when(contractRepository.findAllByBuyerId(buyer.getId()))
        .thenReturn(
            List.of(
                CapitalTransferContract.builder()
                    .id(1L)
                    .state(SELLER_SIGNED)
                    .seller(seller)
                    .buyer(memberFixture().id(3L).build())
                    .transferAmounts(
                        List.of(
                            new CapitalTransferAmount(
                                CAPITAL_PAYMENT,
                                new BigDecimal("40.0"),
                                new BigDecimal("10.0"),
                                new BigDecimal("1.0"))))
                    .build(),
                CapitalTransferContract.builder()
                    .id(2L)
                    .state(SELLER_SIGNED)
                    .seller(seller)
                    .buyer(memberFixture().id(3L).build())
                    .transferAmounts(
                        List.of(
                            new CapitalTransferAmount(
                                CAPITAL_PAYMENT,
                                new BigDecimal("40.0"),
                                new BigDecimal("10.0"),
                                new BigDecimal("1.0"))))
                    .build()));

    when(capitalService.getCapitalRows(seller.getId()))
        .thenReturn(
            List.of(
                new CapitalRow(
                    CAPITAL_PAYMENT,
                    BigDecimal.valueOf(10),
                    BigDecimal.valueOf(90),
                    BigDecimal.valueOf(100),
                    BigDecimal.valueOf(10),
                    Currency.EUR)));
    when(capitalService.getCapitalConcentrationUnitLimit()).thenReturn(BigDecimal.valueOf(105));

    IllegalStateException thrown =
        assertThrows(IllegalStateException.class, () -> validator.validate(seller, buyer, command));
    assertEquals("Buyer would exceed concentration limit after transfer", thrown.getMessage());
  }

  @Test
  void validateThrowsWhenSellerAndBuyerAreSamePerson() {
    var buyerUser = sampleUser().firstName("Olev").lastName("Ostja").build();
    var sellerUser = buyerUser;
    var seller = sellerUser.getMemberOrThrow();
    var buyer = memberFixture().id(1L).user(buyerUser).build();

    var command =
        CreateCapitalTransferContractCommand.builder()
            .buyerMemberId(1L)
            .iban("TEST_IBAN")
            .transferAmounts(
                List.of(
                    new CapitalTransferAmount(
                        CAPITAL_PAYMENT,
                        new BigDecimal("10.0"),
                        new BigDecimal("5.0"),
                        new BigDecimal("1.0"))))
            .build();

    IllegalArgumentException thrown =
        assertThrows(
            IllegalArgumentException.class, () -> validator.validate(seller, buyer, command));
    assertEquals("Seller and buyer cannot be the same person.", thrown.getMessage());
  }

  @Test
  void validateThrowsWhenNoAmountsSpecified() {
    var buyerUser = sampleUser().firstName("Olev").lastName("Ostja").build();
    var sellerUser = buyerUser;
    var seller = sellerUser.getMemberOrThrow();
    var buyer = memberFixture().id(1L).user(buyerUser).build();

    var command =
        CreateCapitalTransferContractCommand.builder()
            .buyerMemberId(1L)
            .iban("TEST_IBAN")
            .transferAmounts(List.of())
            .build();

    IllegalArgumentException thrown =
        assertThrows(
            IllegalArgumentException.class, () -> validator.validate(seller, buyer, command));
    assertEquals("No amounts specified", thrown.getMessage());
  }

  @Test
  void validateThrowsWhenDuplicateTypesSpecified() {
    var buyerUser = sampleUser().firstName("Olev").lastName("Ostja").build();
    var sellerUser = buyerUser;
    var seller = sellerUser.getMemberOrThrow();
    var buyer = memberFixture().id(1L).user(buyerUser).build();

    var command =
        CreateCapitalTransferContractCommand.builder()
            .buyerMemberId(1L)
            .iban("TEST_IBAN")
            .transferAmounts(
                List.of(
                    new CapitalTransferAmount(
                        CAPITAL_PAYMENT,
                        new BigDecimal("10.0"),
                        new BigDecimal("5.0"),
                        new BigDecimal("1.0")),
                    new CapitalTransferAmount(
                        CAPITAL_PAYMENT,
                        new BigDecimal("10.0"),
                        new BigDecimal("5.0"),
                        new BigDecimal("1.0"))))
            .build();

    IllegalArgumentException thrown =
        assertThrows(
            IllegalArgumentException.class, () -> validator.validate(seller, buyer, command));
    assertEquals("Duplicate types specified", thrown.getMessage());
  }

  @Test
  void validateThrowsWhenNonLiquidatableTypesIncluded() {
    var buyerUser = sampleUser().firstName("Olev").lastName("Ostja").build();
    var sellerUser = buyerUser;
    var seller = sellerUser.getMemberOrThrow();
    var buyer = memberFixture().id(1L).user(buyerUser).build();

    var command =
        CreateCapitalTransferContractCommand.builder()
            .buyerMemberId(1L)
            .iban("TEST_IBAN")
            .transferAmounts(
                List.of(
                    new CapitalTransferAmount(
                        INVESTMENT_RETURN,
                        new BigDecimal("10.0"),
                        new BigDecimal("5.0"),
                        new BigDecimal("1.0")),
                    new CapitalTransferAmount(
                        UNVESTED_WORK_COMPENSATION,
                        new BigDecimal("10.0"),
                        new BigDecimal("5.0"),
                        new BigDecimal("1.0"))))
            .build();

    IllegalArgumentException thrown =
        assertThrows(
            IllegalArgumentException.class, () -> validator.validate(seller, buyer, command));
    assertEquals("Non-liquidatable capital types included in command", thrown.getMessage());
  }

  @Test
  void getCapitalBeingSoldInOtherTransfersSummarizesTotalsByType() {
    var user =
        sampleUser()
            .firstName("Olev")
            .lastName("Ostja")
            .member(memberFixture().id(1L).build())
            .build();

    when(contractRepository.findAllBySellerId(user.getMemberId()))
        .thenReturn(
            List.of(
                CapitalTransferContract.builder()
                    .id(1L)
                    .state(SELLER_SIGNED)
                    .seller(user.getMemberOrThrow())
                    .buyer(memberFixture().id(3L).build())
                    .transferAmounts(
                        List.of(
                            new CapitalTransferAmount(
                                CAPITAL_PAYMENT,
                                new BigDecimal("50.0"),
                                new BigDecimal("100.0"),
                                new BigDecimal("1.0")),
                            new CapitalTransferAmount(
                                WORK_COMPENSATION,
                                new BigDecimal("50.0"),
                                new BigDecimal("100.0"),
                                new BigDecimal("1.0"))))
                    .build(),
                CapitalTransferContract.builder()
                    .id(2L)
                    .state(SELLER_SIGNED)
                    .seller(user.getMemberOrThrow())
                    .buyer(memberFixture().id(3L).build())
                    .transferAmounts(
                        List.of(
                            new CapitalTransferAmount(
                                CAPITAL_PAYMENT,
                                new BigDecimal("50.0"),
                                new BigDecimal("100.0"),
                                new BigDecimal("1.0"))))
                    .build(),
                CapitalTransferContract.builder()
                    .id(3L)
                    .state(EXECUTED)
                    .seller(user.getMemberOrThrow())
                    .buyer(memberFixture().id(3L).build())
                    .transferAmounts(
                        List.of(
                            new CapitalTransferAmount(
                                CAPITAL_PAYMENT,
                                new BigDecimal("50.0"),
                                new BigDecimal("100.0"),
                                new BigDecimal("1.0"))))
                    .build(),
                CapitalTransferContract.builder()
                    .id(4L)
                    .state(SELLER_SIGNED)
                    .seller(user.getMemberOrThrow())
                    .buyer(memberFixture().id(3L).build())
                    .transferAmounts(
                        List.of(
                            new CapitalTransferAmount(
                                MEMBERSHIP_BONUS,
                                new BigDecimal("50.0"),
                                new BigDecimal("50.0"),
                                new BigDecimal("1.0"))))
                    .build()));

    var map = validator.getCapitalBeingSoldInOtherTransfers(user.getMemberOrThrow());

    assertEquals(3, map.size());
    assertEquals(0, map.get(CAPITAL_PAYMENT).compareTo(new BigDecimal("200.00")));
    assertEquals(0, map.get(MEMBERSHIP_BONUS).compareTo(new BigDecimal("50.00")));
    assertEquals(0, map.get(WORK_COMPENSATION).compareTo(new BigDecimal("100.00")));
  }

  // Kept as in the original service test: this asserts getCapitalBeingSoldInOtherTransfers
  // (not getCapitalBeingAcquiredInOtherTransfers), pre-existing naming/coverage mismatch
  // preserved verbatim by the pure move.
  @Test
  void getCapitalBeingBoughtSummarizes() {
    var user =
        sampleUser()
            .firstName("Olev")
            .lastName("Ostja")
            .member(memberFixture().id(1L).build())
            .build();

    when(contractRepository.findAllBySellerId(user.getMemberId()))
        .thenReturn(
            List.of(
                CapitalTransferContract.builder()
                    .id(1L)
                    .state(SELLER_SIGNED)
                    .buyer(user.getMemberOrThrow())
                    .seller(memberFixture().id(3L).build())
                    .transferAmounts(
                        List.of(
                            new CapitalTransferAmount(
                                CAPITAL_PAYMENT,
                                new BigDecimal("50.0"),
                                new BigDecimal("100.0"),
                                new BigDecimal("1.0")),
                            new CapitalTransferAmount(
                                WORK_COMPENSATION,
                                new BigDecimal("50.0"),
                                new BigDecimal("100.0"),
                                new BigDecimal("1.0"))))
                    .build(),
                CapitalTransferContract.builder()
                    .id(2L)
                    .state(SELLER_SIGNED)
                    .buyer(user.getMemberOrThrow())
                    .seller(memberFixture().id(3L).build())
                    .transferAmounts(
                        List.of(
                            new CapitalTransferAmount(
                                CAPITAL_PAYMENT,
                                new BigDecimal("50.0"),
                                new BigDecimal("100.0"),
                                new BigDecimal("1.0"))))
                    .build(),
                CapitalTransferContract.builder()
                    .id(3L)
                    .state(EXECUTED)
                    .buyer(user.getMemberOrThrow())
                    .seller(memberFixture().id(3L).build())
                    .transferAmounts(
                        List.of(
                            new CapitalTransferAmount(
                                CAPITAL_PAYMENT,
                                new BigDecimal("50.0"),
                                new BigDecimal("100.0"),
                                new BigDecimal("1.0"))))
                    .build(),
                CapitalTransferContract.builder()
                    .id(4L)
                    .state(SELLER_SIGNED)
                    .buyer(user.getMemberOrThrow())
                    .seller(memberFixture().id(3L).build())
                    .transferAmounts(
                        List.of(
                            new CapitalTransferAmount(
                                MEMBERSHIP_BONUS,
                                new BigDecimal("50.0"),
                                new BigDecimal("50.0"),
                                new BigDecimal("1.0"))))
                    .build()));

    var map = validator.getCapitalBeingSoldInOtherTransfers(user.getMemberOrThrow());

    assertEquals(3, map.size());
    assertEquals(0, map.get(CAPITAL_PAYMENT).compareTo(new BigDecimal("200.00")));
    assertEquals(0, map.get(MEMBERSHIP_BONUS).compareTo(new BigDecimal("50.00")));
    assertEquals(0, map.get(WORK_COMPENSATION).compareTo(new BigDecimal("100.00")));
  }
}
