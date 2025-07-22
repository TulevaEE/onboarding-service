package ee.tuleva.onboarding.capital.transfer;

import static ee.tuleva.onboarding.auth.UserFixture.sampleUser;
import static ee.tuleva.onboarding.capital.event.member.MemberCapitalEventType.CAPITAL_PAYMENT;
import static ee.tuleva.onboarding.capital.event.member.MemberCapitalEventType.MEMBERSHIP_BONUS;
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

    when(capitalService.getCapitalEvents(sellerUser.getMemberId())).thenReturn(List.of());

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
