package ee.tuleva.onboarding.capital.transfer;

import static ee.tuleva.onboarding.auth.UserFixture.sampleUser;
import static ee.tuleva.onboarding.capital.event.member.MemberCapitalEventType.*;
import static ee.tuleva.onboarding.capital.transfer.CapitalTransferContractState.*;
import static ee.tuleva.onboarding.notification.slack.SlackService.SlackChannel.CAPITAL_TRANSFER;
import static ee.tuleva.onboarding.time.TestClockHolder.clock;
import static ee.tuleva.onboarding.user.MemberFixture.memberFixture;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import ee.tuleva.onboarding.auth.AuthenticatedPersonFixture;
import ee.tuleva.onboarding.capital.ApiCapitalEvent;
import ee.tuleva.onboarding.capital.CapitalService;
import ee.tuleva.onboarding.capital.transfer.content.CapitalTransferContractContentService;
import ee.tuleva.onboarding.currency.Currency;
import ee.tuleva.onboarding.notification.email.EmailService;
import ee.tuleva.onboarding.notification.slack.SlackService;
import ee.tuleva.onboarding.user.UserService;
import ee.tuleva.onboarding.user.member.MemberService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CapitalTransferContractServiceTest {

  @Mock private CapitalTransferContractRepository contractRepository;
  @Mock private UserService userService;
  @Mock private MemberService memberService;
  @Mock private EmailService emailService;
  @Mock private CapitalTransferFileService capitalTransferFileService;
  @Mock private CapitalTransferContractContentService contractContentService;
  @Mock private CapitalService capitalService;
  @Mock private SlackService slackService;

  @InjectMocks private CapitalTransferContractService contractService;

  @Test
  @DisplayName("Create capital transfer happy path")
  void create() {

    var buyerUser = sampleUser().firstName("Olev").lastName("Ostja").build();
    var sellerUser = sampleUser().member(memberFixture().id(2L).build()).build();

    var sampleCommand =
        CreateCapitalTransferContractCommand.builder()
            .buyerMemberId(3L)
            .iban("TEST_IBAN")
            .unitCount(BigDecimal.TEN)
            .unitPrice(BigDecimal.TEN)
            .unitsOfMemberBonus(BigDecimal.ONE)
            .build();
    var sellerPerson = AuthenticatedPersonFixture.authenticatedPersonFromUser(sellerUser).build();

    var mockContract = mock(CapitalTransferContract.class);

    when(userService.getById(sellerPerson.getUserId())).thenReturn(Optional.of(sellerUser));
    when(memberService.getById(sampleCommand.getBuyerMemberId()))
        .thenReturn(memberFixture().id(3L).user(buyerUser).build());
    when(contractContentService.generateContractContent(any())).thenReturn(new byte[0]);

    when(contractRepository.save(any())).thenReturn(mockContract);
    when(capitalService.getCapitalEvents(sellerUser.getMemberId()))
        .thenReturn(
            List.of(
                new ApiCapitalEvent(
                    LocalDate.now(clock), CAPITAL_PAYMENT, BigDecimal.valueOf(1000), Currency.EUR),
                new ApiCapitalEvent(
                    LocalDate.now(clock), MEMBERSHIP_BONUS, BigDecimal.valueOf(50), Currency.EUR)));
    when(capitalService.getCapitalConcentrationUnitLimit()).thenReturn(BigDecimal.valueOf(1e8));
    var result = contractService.create(sellerPerson, sampleCommand);

    assertEquals(mockContract, result);
  }

  @Test
  @DisplayName("getContract")
  void getContract() {
    var sellerUser = sampleUser().member(memberFixture().id(2L).build()).build();
    var contract =
        CapitalTransferContract.builder()
            .id(1L)
            .seller(sellerUser.getMemberOrThrow())
            .buyer(memberFixture().id(3L).build())
            .build();

    when(contractRepository.findById(eq(1L))).thenReturn(Optional.of(contract));

    var result = contractService.getContract(1L, sellerUser);
    assertEquals(contract, result);
  }

  @Test
  @DisplayName("getContract not found")
  void getContractNotFound() {
    var sellerUser = sampleUser().member(memberFixture().id(2L).build()).build();

    when(contractRepository.findById(eq(1L))).thenReturn(Optional.empty());

    assertThrows(IllegalArgumentException.class, () -> contractService.getContract(1L, sellerUser));
  }

  @Test
  @DisplayName("getContract not accessible")
  void getContractNotAccessible() {
    var sellerUser = sampleUser().member(memberFixture().id(2L).build()).build();
    var contract =
        CapitalTransferContract.builder()
            .id(1L)
            .seller(memberFixture().id(4L).build())
            .buyer(memberFixture().id(3L).build())
            .build();

    when(contractRepository.findById(eq(1L))).thenReturn(Optional.of(contract));

    assertThrows(IllegalArgumentException.class, () -> contractService.getContract(1L, sellerUser));
  }

  @Test
  @DisplayName("getMyContracts")
  void getMyContracts() {
    var user = sampleUser().member(memberFixture().id(2L).build()).build();

    var mockContract = mock(CapitalTransferContract.class);
    var mockContract2 = mock(CapitalTransferContract.class);
    var mockContract3 = mock(CapitalTransferContract.class);

    when(contractRepository.findAllByBuyerId(eq(user.getMemberId())))
        .thenReturn(List.of(mockContract));
    when(contractRepository.findAllBySellerId(eq(user.getMemberId())))
        .thenReturn(List.of(mockContract2, mockContract3));

    var result = contractService.getMyContracts(user);
    assertEquals(3, result.size());
  }

  @Test
  @DisplayName("getMyContracts empty")
  void getMyContractsEmpty() {
    var user = sampleUser().member(memberFixture().id(2L).build()).build();

    when(contractRepository.findAllByBuyerId(eq(user.getMemberId()))).thenReturn(List.of());
    when(contractRepository.findAllBySellerId(eq(user.getMemberId()))).thenReturn(List.of());

    var result = contractService.getMyContracts(user);
    assertEquals(0, result.size());
  }

  @Test
  @DisplayName("updateState payment confirmed by buyer")
  void updateStateConfirmedByBuyer() {

    for (CapitalTransferContractState state : CapitalTransferContractState.values()) {

      var user = sampleUser().member(memberFixture().id(2L).build()).build();
      var contract =
          CapitalTransferContract.builder()
              .id(1L)
              .state(state)
              .seller(memberFixture().id(3L).build())
              .buyer(user.getMemberOrThrow())
              .build();

      when(contractRepository.findById(eq(1L))).thenReturn(Optional.of(contract));

      if (state == BUYER_SIGNED) {
        when(contractRepository.save(any(CapitalTransferContract.class))).thenReturn(contract);
        var result = contractService.updateState(1L, PAYMENT_CONFIRMED_BY_BUYER, user);
        assertEquals(contract, result);

      } else {
        assertThrows(
            IllegalArgumentException.class,
            () -> contractService.updateState(1L, PAYMENT_CONFIRMED_BY_BUYER, user));
      }
    }
  }

  @Test
  @DisplayName("updateState payment confirmed by buyer throws when attempted by seller")
  void updateStateConfirmedByBuyerBySeller() {

    var user = sampleUser().member(memberFixture().id(2L).build()).build();
    var contract =
        CapitalTransferContract.builder()
            .id(1L)
            .state(BUYER_SIGNED)
            .seller(user.getMemberOrThrow())
            .buyer(memberFixture().id(3L).build())
            .build();

    when(contractRepository.findById(eq(1L))).thenReturn(Optional.of(contract));

    assertThrows(
        IllegalStateException.class,
        () -> contractService.updateState(1L, PAYMENT_CONFIRMED_BY_BUYER, user));
  }

  @Test
  @DisplayName("updateState payment confirmed by seller")
  void updateStateConfirmedBySeller() {

    for (CapitalTransferContractState state : CapitalTransferContractState.values()) {

      var user = sampleUser().member(memberFixture().id(2L).build()).build();
      var contract =
          CapitalTransferContract.builder()
              .id(1L)
              .state(state)
              .buyer(memberFixture().id(3L).build())
              .seller(user.getMemberOrThrow())
              .build();

      when(contractRepository.findById(eq(1L))).thenReturn(Optional.of(contract));

      if (state == PAYMENT_CONFIRMED_BY_BUYER) {
        when(contractRepository.save(any(CapitalTransferContract.class))).thenReturn(contract);
        var result = contractService.updateState(1L, PAYMENT_CONFIRMED_BY_SELLER, user);
        assertEquals(contract, result);
        verify(slackService).sendMessage(anyString(), eq(CAPITAL_TRANSFER));

      } else {
        assertThrows(
            IllegalArgumentException.class,
            () -> contractService.updateState(1L, PAYMENT_CONFIRMED_BY_SELLER, user));
      }
    }
  }

  @Test
  @DisplayName("updateState payment confirmed by seller throws when attempted by buyer")
  void updateStateConfirmedBySellerByBuyer() {

    var user = sampleUser().member(memberFixture().id(2L).build()).build();
    var contract =
        CapitalTransferContract.builder()
            .id(1L)
            .state(PAYMENT_CONFIRMED_BY_BUYER)
            .buyer(user.getMemberOrThrow())
            .seller(memberFixture().id(3L).build())
            .build();

    when(contractRepository.findById(eq(1L))).thenReturn(Optional.of(contract));

    assertThrows(
        IllegalStateException.class,
        () -> contractService.updateState(1L, PAYMENT_CONFIRMED_BY_SELLER, user));
  }

  @Test
  @DisplayName("signBySeller throws when attempted by buyer")
  void signBySellerByBuyer() {

    var user = sampleUser().member(memberFixture().id(2L).build()).build();
    var contract =
        CapitalTransferContract.builder()
            .id(1L)
            .state(PAYMENT_CONFIRMED_BY_BUYER)
            .buyer(user.getMemberOrThrow())
            .seller(memberFixture().id(3L).build())
            .build();

    when(contractRepository.findById(eq(1L))).thenReturn(Optional.of(contract));

    assertThrows(
        IllegalStateException.class, () -> contractService.signBySeller(1L, new byte[0], user));
  }

  @Test
  @DisplayName("signByBuyer throws when attempted by seller")
  void signByBuyerBySeller() {

    var user = sampleUser().member(memberFixture().id(2L).build()).build();
    var contract =
        CapitalTransferContract.builder()
            .id(1L)
            .state(PAYMENT_CONFIRMED_BY_BUYER)
            .seller(user.getMemberOrThrow())
            .buyer(memberFixture().id(3L).build())
            .build();

    when(contractRepository.findById(eq(1L))).thenReturn(Optional.of(contract));

    assertThrows(
        IllegalStateException.class, () -> contractService.signByBuyer(1L, new byte[0], user));
  }

  @Test
  @DisplayName("Create capital transfer throws when not enough capital")
  void createNotEnoughCapital() {

    var buyerUser = sampleUser().firstName("Olev").lastName("Ostja").build();
    var sellerUser = sampleUser().member(memberFixture().id(2L).build()).build();

    var sampleCommand =
        CreateCapitalTransferContractCommand.builder()
            .buyerMemberId(3L)
            .iban("TEST_IBAN")
            .unitCount(BigDecimal.TEN)
            .unitPrice(BigDecimal.TEN)
            .unitsOfMemberBonus(BigDecimal.ONE)
            .build();
    var sellerPerson = AuthenticatedPersonFixture.authenticatedPersonFromUser(sellerUser).build();

    var mockContract = mock(CapitalTransferContract.class);

    when(userService.getById(sellerPerson.getUserId())).thenReturn(Optional.of(sellerUser));
    when(memberService.getById(sampleCommand.getBuyerMemberId()))
        .thenReturn(memberFixture().id(3L).user(buyerUser).build());

    when(capitalService.getCapitalEvents(sellerUser.getMemberId()))
        .thenReturn(
            List.of(
                new ApiCapitalEvent(
                    LocalDate.now(clock),
                    UNVESTED_WORK_COMPENSATION,
                    BigDecimal.valueOf(1000),
                    Currency.EUR)));

    IllegalStateException thrown =
        assertThrows(
            IllegalStateException.class, () -> contractService.create(sellerPerson, sampleCommand));
    assertEquals("Seller does not have enough member capital", thrown.getMessage());
  }

  @Test
  @DisplayName("Create capital transfer throws when not enough member bonus")
  void createNotEnoughMemberBonus() {

    var buyerUser = sampleUser().firstName("Olev").lastName("Ostja").build();
    var sellerUser = sampleUser().member(memberFixture().id(2L).build()).build();

    var sampleCommand =
        CreateCapitalTransferContractCommand.builder()
            .buyerMemberId(3L)
            .iban("TEST_IBAN")
            .unitCount(BigDecimal.TEN)
            .unitPrice(BigDecimal.TEN)
            .unitsOfMemberBonus(BigDecimal.TEN)
            .build();
    var sellerPerson = AuthenticatedPersonFixture.authenticatedPersonFromUser(sellerUser).build();

    when(userService.getById(sellerPerson.getUserId())).thenReturn(Optional.of(sellerUser));
    when(memberService.getById(sampleCommand.getBuyerMemberId()))
        .thenReturn(memberFixture().id(3L).user(buyerUser).build());

    when(capitalService.getCapitalEvents(sellerUser.getMemberId()))
        .thenReturn(
            List.of(
                new ApiCapitalEvent(
                    LocalDate.now(clock), CAPITAL_PAYMENT, BigDecimal.valueOf(1000), Currency.EUR),
                new ApiCapitalEvent(
                    LocalDate.now(clock), MEMBERSHIP_BONUS, BigDecimal.valueOf(5), Currency.EUR)));

    IllegalStateException thrown =
        assertThrows(
            IllegalStateException.class, () -> contractService.create(sellerPerson, sampleCommand));
    assertEquals("Seller does not have enough member bonus", thrown.getMessage());
  }

  @Test
  @DisplayName("Create capital transfer throws when exceeding concentration limit")
  void createExceedConcentrationLimit() {

    var buyerUser = sampleUser().firstName("Olev").lastName("Ostja").build();
    var sellerUser = sampleUser().member(memberFixture().id(2L).build()).build();

    var sampleCommand =
        CreateCapitalTransferContractCommand.builder()
            .buyerMemberId(3L)
            .iban("TEST_IBAN")
            .unitCount(BigDecimal.valueOf(100))
            .unitPrice(BigDecimal.TEN)
            .unitsOfMemberBonus(BigDecimal.ONE)
            .build();
    var sellerPerson = AuthenticatedPersonFixture.authenticatedPersonFromUser(sellerUser).build();

    when(userService.getById(sellerPerson.getUserId())).thenReturn(Optional.of(sellerUser));
    when(memberService.getById(sampleCommand.getBuyerMemberId()))
        .thenReturn(memberFixture().id(3L).user(buyerUser).build());

    when(capitalService.getCapitalEvents(sellerUser.getMemberId()))
        .thenReturn(
            List.of(
                new ApiCapitalEvent(
                    LocalDate.now(clock), CAPITAL_PAYMENT, BigDecimal.valueOf(1000), Currency.EUR),
                new ApiCapitalEvent(
                    LocalDate.now(clock), MEMBERSHIP_BONUS, BigDecimal.valueOf(5), Currency.EUR)));
    when(capitalService.getCapitalConcentrationUnitLimit()).thenReturn(BigDecimal.valueOf(10));

    IllegalStateException thrown =
        assertThrows(
            IllegalStateException.class, () -> contractService.create(sellerPerson, sampleCommand));
    assertEquals("Buyer would exceed concentration limit after transfer", thrown.getMessage());
  }

  @Test
  @DisplayName("Create capital transfer throws when unit price below minimum")
  void createPriceBelowMinimum() {

    var buyerUser = sampleUser().firstName("Olev").lastName("Ostja").build();
    var sellerUser = sampleUser().member(memberFixture().id(2L).build()).build();

    var sampleCommand =
        CreateCapitalTransferContractCommand.builder()
            .buyerMemberId(3L)
            .iban("TEST_IBAN")
            .unitCount(BigDecimal.TEN)
            .unitPrice(BigDecimal.valueOf(0.5))
            .unitsOfMemberBonus(BigDecimal.ONE)
            .build();
    var sellerPerson = AuthenticatedPersonFixture.authenticatedPersonFromUser(sellerUser).build();

    when(userService.getById(sellerPerson.getUserId())).thenReturn(Optional.of(sellerUser));
    when(memberService.getById(sampleCommand.getBuyerMemberId()))
        .thenReturn(memberFixture().id(3L).user(buyerUser).build());

    when(capitalService.getCapitalEvents(sellerUser.getMemberId()))
        .thenReturn(
            List.of(
                new ApiCapitalEvent(
                    LocalDate.now(clock), CAPITAL_PAYMENT, BigDecimal.valueOf(1000), Currency.EUR),
                new ApiCapitalEvent(
                    LocalDate.now(clock), MEMBERSHIP_BONUS, BigDecimal.valueOf(5), Currency.EUR)));
    when(capitalService.getCapitalConcentrationUnitLimit()).thenReturn(BigDecimal.valueOf(1e8));

    IllegalArgumentException thrown =
        assertThrows(
            IllegalArgumentException.class,
            () -> contractService.create(sellerPerson, sampleCommand));
    assertEquals("Unit price below minimum", thrown.getMessage());
  }

  @Test
  @DisplayName("Create capital transfer throws buyer seller same person")
  void createBuyerSellerSamePerson() {

    var buyerUser = sampleUser().firstName("Olev").lastName("Ostja").build();
    var sellerUser = buyerUser;

    var sampleCommand =
        CreateCapitalTransferContractCommand.builder()
            .buyerMemberId(1L)
            .iban("TEST_IBAN")
            .unitCount(BigDecimal.TEN)
            .unitPrice(BigDecimal.TEN)
            .unitsOfMemberBonus(BigDecimal.ZERO)
            .build();
    var sellerPerson = AuthenticatedPersonFixture.authenticatedPersonFromUser(sellerUser).build();

    when(userService.getById(sellerPerson.getUserId())).thenReturn(Optional.of(sellerUser));
    when(memberService.getById(sampleCommand.getBuyerMemberId()))
        .thenReturn(memberFixture().id(1L).user(buyerUser).build());

    when(capitalService.getCapitalEvents(sellerUser.getMemberId()))
        .thenReturn(
            List.of(
                new ApiCapitalEvent(
                    LocalDate.now(clock), CAPITAL_PAYMENT, BigDecimal.valueOf(1000), Currency.EUR),
                new ApiCapitalEvent(
                    LocalDate.now(clock), MEMBERSHIP_BONUS, BigDecimal.valueOf(5), Currency.EUR)));
    when(capitalService.getCapitalConcentrationUnitLimit()).thenReturn(BigDecimal.valueOf(1e8));

    IllegalArgumentException thrown =
        assertThrows(
            IllegalArgumentException.class,
            () -> contractService.create(sellerPerson, sampleCommand));
    assertEquals("Seller and buyer cannot be the same person.", thrown.getMessage());
  }
}
