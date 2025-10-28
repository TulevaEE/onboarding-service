package ee.tuleva.onboarding.capital.transfer;

import static ee.tuleva.onboarding.auth.UserFixture.sampleUser;
import static ee.tuleva.onboarding.capital.event.member.MemberCapitalEventType.*;
import static ee.tuleva.onboarding.capital.transfer.CapitalTransferContractState.*;
import static ee.tuleva.onboarding.event.TrackableEventType.CAPITAL_TRANSFER_STATE_CHANGE;
import static ee.tuleva.onboarding.notification.slack.SlackService.SlackChannel.CAPITAL_TRANSFER;
import static ee.tuleva.onboarding.user.MemberFixture.memberFixture;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.microtripit.mandrillapp.lutung.view.MandrillMessageStatus;
import ee.tuleva.onboarding.auth.AuthenticatedPersonFixture;
import ee.tuleva.onboarding.capital.CapitalRow;
import ee.tuleva.onboarding.capital.CapitalService;
import ee.tuleva.onboarding.capital.event.AggregatedCapitalEvent;
import ee.tuleva.onboarding.capital.event.AggregatedCapitalEventRepository;
import ee.tuleva.onboarding.capital.transfer.CapitalTransferContract.CapitalTransferAmount;
import ee.tuleva.onboarding.capital.transfer.content.CapitalTransferContractContentService;
import ee.tuleva.onboarding.currency.Currency;
import ee.tuleva.onboarding.epis.contact.ContactDetailsService;
import ee.tuleva.onboarding.event.TrackableEvent;
import ee.tuleva.onboarding.mandate.email.persistence.Email;
import ee.tuleva.onboarding.mandate.email.persistence.EmailPersistenceService;
import ee.tuleva.onboarding.notification.email.EmailService;
import ee.tuleva.onboarding.notification.slack.SlackService;
import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.user.UserService;
import ee.tuleva.onboarding.user.member.MemberService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatcher;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class CapitalTransferContractServiceTest {

  @Mock private CapitalTransferContractRepository contractRepository;
  @Mock private UserService userService;
  @Mock private MemberService memberService;
  @Mock private EmailService emailService;
  @Mock private EmailPersistenceService emailPersistenceService;
  @Mock private CapitalTransferFileService capitalTransferFileService;
  @Mock private CapitalTransferContractContentService contractContentService;
  @Mock private CapitalService capitalService;
  @Mock private ContactDetailsService contactDetailsService;
  @Mock private SlackService slackService;
  @Mock private ApplicationEventPublisher eventPublisher;
  @Mock private AggregatedCapitalEventRepository aggregatedCapitalEventRepository;

  @InjectMocks private CapitalTransferContractService contractService;

  @BeforeEach
  void setUp() {
    lenient()
        .when(aggregatedCapitalEventRepository.findTopByOrderByDateDesc())
        .thenReturn(
            AggregatedCapitalEvent.builder().ownershipUnitPrice(new BigDecimal("1.0")).build());
  }

  @Test
  @DisplayName("Create capital transfer happy path")
  void create() {

    var buyerUser = sampleUser().firstName("Olev").lastName("Ostja").build();
    var sellerUser = sampleUser().member(memberFixture().id(2L).build()).build();

    var sampleCommand =
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
    var sellerPerson = AuthenticatedPersonFixture.authenticatedPersonFromUser(sellerUser).build();

    var mockContract = mock(CapitalTransferContract.class);

    when(contractRepository.findAllBySellerId(sellerUser.getMemberId())).thenReturn(List.of());
    when(userService.getById(sellerPerson.getUserId())).thenReturn(Optional.of(sellerUser));
    when(memberService.getById(sampleCommand.getBuyerMemberId()))
        .thenReturn(memberFixture().id(3L).user(buyerUser).build());
    when(contractContentService.generateContractContent(any())).thenReturn(new byte[0]);

    when(contractRepository.save(any())).thenReturn(mockContract);
    when(capitalService.getCapitalRows(sellerUser.getMemberId()))
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
    var result = contractService.create(sellerPerson, sampleCommand);

    assertEquals(mockContract, result);
  }

  @Test
  @DisplayName("Create capital transfer happy path with active transfers")
  void createWithOtherTransfers() {

    var buyerUser = sampleUser().firstName("Olev").lastName("Ostja").build();
    var sellerUser = sampleUser().member(memberFixture().id(2L).build()).build();

    var sampleCommand =
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
    var sellerPerson = AuthenticatedPersonFixture.authenticatedPersonFromUser(sellerUser).build();

    var mockContract = mock(CapitalTransferContract.class);

    when(contractRepository.findAllBySellerId(sellerUser.getMemberId()))
        .thenReturn(
            List.of(
                CapitalTransferContract.builder()
                    .id(1L)
                    .state(SELLER_SIGNED)
                    .seller(sellerUser.getMemberOrThrow())
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
                    .seller(sellerUser.getMemberOrThrow())
                    .buyer(memberFixture().id(3L).build())
                    .transferAmounts(
                        List.of(
                            new CapitalTransferAmount(
                                CAPITAL_PAYMENT,
                                new BigDecimal("40.0"),
                                new BigDecimal("450.0"),
                                new BigDecimal("1.0"))))
                    .build()));
    when(userService.getById(sellerPerson.getUserId())).thenReturn(Optional.of(sellerUser));
    when(memberService.getById(sampleCommand.getBuyerMemberId()))
        .thenReturn(memberFixture().id(3L).user(buyerUser).build());
    when(contractContentService.generateContractContent(any())).thenReturn(new byte[0]);

    when(contractRepository.save(any())).thenReturn(mockContract);
    when(capitalService.getCapitalRows(sellerUser.getMemberId()))
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
  @DisplayName("updateStateByUser payment confirmed by buyer")
  void updateStateByUserConfirmedByBuyer() {

    for (CapitalTransferContractState state : CapitalTransferContractState.values()) {

      var user = sampleUser().member(memberFixture().id(2L).build()).build();
      var contract =
          CapitalTransferContract.builder()
              .id(1L)
              .state(state)
              .seller(memberFixture().id(3L).build())
              .buyer(user.getMemberOrThrow())
              .digiDocContainer(new byte[0])
              .build();

      when(contractRepository.findById(eq(1L))).thenReturn(Optional.of(contract));

      if (state == BUYER_SIGNED) {
        when(contractRepository.save(any(CapitalTransferContract.class))).thenReturn(contract);
        //
        // when(contactDetailsService.getContactDetails(user)).thenReturn(contactDetailsFixture());
        when(emailPersistenceService.save(any(), any(), any(), any()))
            .thenReturn(Email.builder().id(1L).build());
        when(emailService.send(
                eq(contract.getSeller().getUser()),
                any(),
                eq("capital_transfer_confirmed_by_buyer_et")))
            .thenReturn(Optional.of(new MandrillMessageStatus()));

        var result = contractService.updateStateByUser(1L, PAYMENT_CONFIRMED_BY_BUYER, user);
        assertEquals(contract, result);
        verify(eventPublisher)
            .publishEvent(
                argThat(getStateChangeEventMatcher(user, state, PAYMENT_CONFIRMED_BY_BUYER)));

      } else {
        assertThrows(
            IllegalArgumentException.class,
            () -> contractService.updateStateByUser(1L, PAYMENT_CONFIRMED_BY_BUYER, user));
      }
    }
  }

  @Test
  @DisplayName("updateStateByUser payment confirmed by buyer throws when attempted by seller")
  void updateStateByUserConfirmedByBuyerBySeller() {

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
        () -> contractService.updateStateByUser(1L, PAYMENT_CONFIRMED_BY_BUYER, user));
  }

  @Test
  @DisplayName("updateStateByUser payment confirmed by seller")
  void updateStateByUserConfirmedBySeller() {

    for (CapitalTransferContractState state : CapitalTransferContractState.values()) {

      var user = sampleUser().member(memberFixture().id(2L).build()).build();
      var contract =
          CapitalTransferContract.builder()
              .id(1L)
              .state(state)
              .buyer(memberFixture().id(3L).build())
              .seller(user.getMemberOrThrow())
              .digiDocContainer(new byte[0])
              .build();

      when(contractRepository.findById(eq(1L))).thenReturn(Optional.of(contract));

      if (state == PAYMENT_CONFIRMED_BY_BUYER) {
        when(contractRepository.save(any(CapitalTransferContract.class))).thenReturn(contract);
        when(emailPersistenceService.save(any(), any(), any(), any()))
            .thenReturn(Email.builder().id(1L).build());
        //
        // when(contactDetailsService.getContactDetails(user)).thenReturn(contactDetailsFixture());
        when(emailService.send(
                eq(contract.getBuyer().getUser()),
                any(),
                eq("capital_transfer_confirmed_by_seller_et")))
            .thenReturn(Optional.of(new MandrillMessageStatus()));

        var result = contractService.updateStateByUser(1L, PAYMENT_CONFIRMED_BY_SELLER, user);
        assertEquals(contract, result);
        verify(slackService).sendMessage(anyString(), eq(CAPITAL_TRANSFER));

        // Verify that email is sent to the buyer (not the seller)
        verify(emailService)
            .send(
                eq(contract.getBuyer().getUser()),
                any(),
                eq("capital_transfer_confirmed_by_seller_et"));

        verify(eventPublisher)
            .publishEvent(
                argThat(getStateChangeEventMatcher(user, state, PAYMENT_CONFIRMED_BY_SELLER)));

      } else {
        assertThrows(
            IllegalArgumentException.class,
            () -> contractService.updateStateByUser(1L, PAYMENT_CONFIRMED_BY_SELLER, user));
      }
    }
  }

  @Test
  @DisplayName("updateStateByUser payment confirmed by seller throws when attempted by buyer")
  void updateStateByUserConfirmedBySellerByBuyer() {

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
        () -> contractService.updateStateByUser(1L, PAYMENT_CONFIRMED_BY_SELLER, user));
  }

  @Test
  @DisplayName("updateStateBySystem approved and notified by board")
  void updateStateBySystemConfirmedBySeller() {
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

      if (state == EXECUTED) {
        when(contractRepository.save(any(CapitalTransferContract.class))).thenReturn(contract);
        var result = contractService.updateStateBySystem(1L, APPROVED_AND_NOTIFIED);
        assertEquals(contract, result);

      } else {
        assertThrows(
            IllegalArgumentException.class,
            () -> contractService.updateStateByUser(1L, APPROVED_AND_NOTIFIED, user));
      }
    }
  }

  @Test
  @DisplayName("signBySeller")
  void signBySeller() {

    var user = sampleUser().member(memberFixture().id(2L).build()).build();
    var contract =
        CapitalTransferContract.builder()
            .id(1L)
            .state(CREATED)
            .buyer(memberFixture().id(3L).build())
            .seller(user.getMemberOrThrow())
            .build();

    when(contractRepository.findById(eq(1L))).thenReturn(Optional.of(contract));

    when(contractRepository.save(any(CapitalTransferContract.class))).thenReturn(contract);
    //    when(contactDetailsService.getContactDetails(user)).thenReturn(contactDetailsFixture());
    when(emailPersistenceService.save(any(), any(), any(), any()))
        .thenReturn(Email.builder().id(1L).build());
    when(emailService.send(
            eq(contract.getBuyer().getUser()), any(), eq("capital_transfer_buyer_to_sign_et")))
        .thenReturn(Optional.of(new MandrillMessageStatus()));

    assertDoesNotThrow(() -> contractService.signBySeller(1L, new byte[0], user));

    verify(eventPublisher)
        .publishEvent(argThat(getStateChangeEventMatcher(user, CREATED, SELLER_SIGNED)));
  }

  @Test
  @DisplayName("signBySeller throws when attempted by buyer")
  void signBySellerByBuyer() {

    var user = sampleUser().member(memberFixture().id(2L).build()).build();
    var contract =
        CapitalTransferContract.builder()
            .id(1L)
            .state(CREATED)
            .buyer(user.getMemberOrThrow())
            .seller(memberFixture().id(3L).build())
            .build();

    when(contractRepository.findById(eq(1L))).thenReturn(Optional.of(contract));

    assertThrows(
        IllegalStateException.class, () -> contractService.signBySeller(1L, new byte[0], user));
    verify(eventPublisher, never()).publishEvent(any());
  }

  @Test
  @DisplayName("signByBuyer")
  void signByBuyer() {
    var user = sampleUser().member(memberFixture().id(2L).build()).build();
    var contract =
        CapitalTransferContract.builder()
            .id(1L)
            .state(SELLER_SIGNED)
            .buyer(user.getMemberOrThrow())
            .seller(memberFixture().id(3L).build())
            .build();

    when(contractRepository.findById(eq(1L))).thenReturn(Optional.of(contract));

    when(contractRepository.save(any(CapitalTransferContract.class))).thenReturn(contract);

    assertDoesNotThrow(() -> contractService.signByBuyer(1L, new byte[0], user));
    verify(eventPublisher)
        .publishEvent(argThat(getStateChangeEventMatcher(user, SELLER_SIGNED, BUYER_SIGNED)));
  }

  @Test
  @DisplayName("signByBuyer throws when attempted by seller")
  void signByBuyerBySeller() {

    var user = sampleUser().member(memberFixture().id(2L).build()).build();
    var contract =
        CapitalTransferContract.builder()
            .id(1L)
            .state(SELLER_SIGNED)
            .seller(user.getMemberOrThrow())
            .buyer(memberFixture().id(3L).build())
            .build();

    when(contractRepository.findById(eq(1L))).thenReturn(Optional.of(contract));

    assertThrows(
        IllegalStateException.class, () -> contractService.signByBuyer(1L, new byte[0], user));
    verify(eventPublisher, never()).publishEvent(any());
  }

  @Test
  @DisplayName("Create capital transfer throws when capital already being sold in other transfers")
  void createCapitalAlreadyBeingSoldInOtherTransfers() {

    var buyerUser = sampleUser().firstName("Olev").lastName("Ostja").build();
    var sellerUser = sampleUser().member(memberFixture().id(2L).build()).build();

    var sampleCommand =
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
    var sellerPerson = AuthenticatedPersonFixture.authenticatedPersonFromUser(sellerUser).build();

    var mockContract = mock(CapitalTransferContract.class);

    when(contractRepository.findAllBySellerId(sellerUser.getMemberId()))
        .thenReturn(
            List.of(
                CapitalTransferContract.builder()
                    .id(1L)
                    .state(SELLER_SIGNED)
                    .seller(sellerUser.getMemberOrThrow())
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
                    .seller(sellerUser.getMemberOrThrow())
                    .buyer(memberFixture().id(3L).build())
                    .transferAmounts(
                        List.of(
                            new CapitalTransferAmount(
                                CAPITAL_PAYMENT,
                                new BigDecimal("50.0"),
                                new BigDecimal("100.0"),
                                new BigDecimal("1.0"))))
                    .build()));
    when(userService.getById(sellerPerson.getUserId())).thenReturn(Optional.of(sellerUser));
    when(memberService.getById(sampleCommand.getBuyerMemberId()))
        .thenReturn(memberFixture().id(3L).user(buyerUser).build());

    when(capitalService.getCapitalRows(sellerUser.getMemberId()))
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
        assertThrows(
            IllegalStateException.class, () -> contractService.create(sellerPerson, sampleCommand));
    assertEquals("Seller does not have enough member capital", thrown.getMessage());
  }

  @Test
  @DisplayName("Create capital transfer throws when not enough capital")
  void createCapitalNotEnoughCapital() {

    var buyerUser = sampleUser().firstName("Olev").lastName("Ostja").build();
    var sellerUser = sampleUser().member(memberFixture().id(2L).build()).build();

    var sampleCommand =
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
    var sellerPerson = AuthenticatedPersonFixture.authenticatedPersonFromUser(sellerUser).build();

    when(contractRepository.findAllBySellerId(sellerUser.getMemberId())).thenReturn(List.of());
    when(userService.getById(sellerPerson.getUserId())).thenReturn(Optional.of(sellerUser));
    when(memberService.getById(sampleCommand.getBuyerMemberId()))
        .thenReturn(memberFixture().id(3L).user(buyerUser).build());

    when(capitalService.getCapitalRows(sellerUser.getMemberId()))
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
        assertThrows(
            IllegalStateException.class, () -> contractService.create(sellerPerson, sampleCommand));
    assertEquals("Seller does not have enough member capital", thrown.getMessage());
  }

  @Test
  @DisplayName(
      "Create capital transfer throws when some capital available but others already being sold in other transfers")
  void createCapitalSomeAvailableOthersAlreadyBeingSoldIn() {

    var buyerUser = sampleUser().firstName("Olev").lastName("Ostja").build();
    var sellerUser = sampleUser().member(memberFixture().id(2L).build()).build();

    var sampleCommand =
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
    var sellerPerson = AuthenticatedPersonFixture.authenticatedPersonFromUser(sellerUser).build();

    when(contractRepository.findAllBySellerId(sellerUser.getMemberId()))
        .thenReturn(
            List.of(
                CapitalTransferContract.builder()
                    .id(1L)
                    .state(SELLER_SIGNED)
                    .seller(sellerUser.getMemberOrThrow())
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
                    .seller(sellerUser.getMemberOrThrow())
                    .buyer(memberFixture().id(3L).build())
                    .transferAmounts(
                        List.of(
                            new CapitalTransferAmount(
                                MEMBERSHIP_BONUS,
                                new BigDecimal("50.0"),
                                new BigDecimal("5.0"),
                                new BigDecimal("1.0"))))
                    .build()));
    when(userService.getById(sellerPerson.getUserId())).thenReturn(Optional.of(sellerUser));
    when(memberService.getById(sampleCommand.getBuyerMemberId()))
        .thenReturn(memberFixture().id(3L).user(buyerUser).build());

    when(capitalService.getCapitalRows(sellerUser.getMemberId()))
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
        assertThrows(
            IllegalStateException.class, () -> contractService.create(sellerPerson, sampleCommand));
    assertEquals("Seller does not have enough member capital", thrown.getMessage());
  }

  @Test
  @DisplayName("Create capital transfer throws on zero amounts")
  void createZeroAmounts() {

    var buyerUser = sampleUser().firstName("Olev").lastName("Ostja").build();
    var sellerUser = sampleUser().member(memberFixture().id(2L).build()).build();

    var sampleCommand =
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
    var sellerPerson = AuthenticatedPersonFixture.authenticatedPersonFromUser(sellerUser).build();

    when(userService.getById(sellerPerson.getUserId())).thenReturn(Optional.of(sellerUser));
    when(memberService.getById(sampleCommand.getBuyerMemberId()))
        .thenReturn(memberFixture().id(3L).user(buyerUser).build());

    IllegalArgumentException thrown =
        assertThrows(
            IllegalArgumentException.class,
            () -> contractService.create(sellerPerson, sampleCommand));
    assertEquals("Amounts or prices have negative or zero values", thrown.getMessage());
  }

  @Test
  @DisplayName("Create capital transfer throws on negative amounts")
  void createNegativeAmounts() {

    var buyerUser = sampleUser().firstName("Olev").lastName("Ostja").build();
    var sellerUser = sampleUser().member(memberFixture().id(2L).build()).build();

    var sampleCommand =
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
    var sellerPerson = AuthenticatedPersonFixture.authenticatedPersonFromUser(sellerUser).build();

    when(userService.getById(sellerPerson.getUserId())).thenReturn(Optional.of(sellerUser));
    when(memberService.getById(sampleCommand.getBuyerMemberId()))
        .thenReturn(memberFixture().id(3L).user(buyerUser).build());

    IllegalArgumentException thrown =
        assertThrows(
            IllegalArgumentException.class,
            () -> contractService.create(sellerPerson, sampleCommand));
    assertEquals("Amounts or prices have negative or zero values", thrown.getMessage());
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
            .transferAmounts(
                List.of(
                    new CapitalTransferAmount(
                        MEMBERSHIP_BONUS,
                        new BigDecimal("100.0"),
                        new BigDecimal("10.0"),
                        new BigDecimal("1.0"))))
            .build();
    var sellerPerson = AuthenticatedPersonFixture.authenticatedPersonFromUser(sellerUser).build();

    when(contractRepository.findAllBySellerId(sellerUser.getMemberId())).thenReturn(List.of());
    when(userService.getById(sellerPerson.getUserId())).thenReturn(Optional.of(sellerUser));
    when(memberService.getById(sampleCommand.getBuyerMemberId()))
        .thenReturn(memberFixture().id(3L).user(buyerUser).build());

    when(capitalService.getCapitalRows(sellerUser.getMemberId()))
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
        assertThrows(
            IllegalStateException.class, () -> contractService.create(sellerPerson, sampleCommand));
    assertEquals("Seller does not have enough member capital", thrown.getMessage());
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
            .transferAmounts(
                List.of(
                    new CapitalTransferAmount(
                        CAPITAL_PAYMENT,
                        new BigDecimal("100.0"),
                        new BigDecimal("10.0"),
                        new BigDecimal("1.0"))))
            .build();
    var sellerPerson = AuthenticatedPersonFixture.authenticatedPersonFromUser(sellerUser).build();

    when(contractRepository.findAllBySellerId(sellerUser.getMemberId())).thenReturn(List.of());
    when(userService.getById(sellerPerson.getUserId())).thenReturn(Optional.of(sellerUser));
    when(memberService.getById(sampleCommand.getBuyerMemberId()))
        .thenReturn(memberFixture().id(3L).user(buyerUser).build());

    when(capitalService.getCapitalRows(sellerUser.getMemberId()))
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
        assertThrows(
            IllegalStateException.class, () -> contractService.create(sellerPerson, sampleCommand));
    assertEquals("Buyer would exceed concentration limit after transfer", thrown.getMessage());
  }

  @Test
  @DisplayName(
      "Create capital transfer throws when exceeding concentration limit with other transfers")
  void createExceedConcentrationLimitWithOtherTransfers() {

    var buyerUser =
        sampleUser()
            .member(memberFixture().id(3L).build())
            .firstName("Olev")
            .lastName("Ostja")
            .build();
    var sellerUser = sampleUser().member(memberFixture().id(2L).build()).build();

    var sampleCommand =
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
    var sellerPerson = AuthenticatedPersonFixture.authenticatedPersonFromUser(sellerUser).build();

    when(contractRepository.findAllByBuyerId(buyerUser.getMemberId()))
        .thenReturn(
            List.of(
                CapitalTransferContract.builder()
                    .id(1L)
                    .state(SELLER_SIGNED)
                    .seller(sellerUser.getMemberOrThrow())
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
                    .seller(sellerUser.getMemberOrThrow())
                    .buyer(memberFixture().id(3L).build())
                    .transferAmounts(
                        List.of(
                            new CapitalTransferAmount(
                                CAPITAL_PAYMENT,
                                new BigDecimal("40.0"),
                                new BigDecimal("10.0"),
                                new BigDecimal("1.0"))))
                    .build()));

    when(userService.getById(sellerPerson.getUserId())).thenReturn(Optional.of(sellerUser));
    when(memberService.getById(sampleCommand.getBuyerMemberId()))
        .thenReturn(memberFixture().id(3L).user(buyerUser).build());

    when(capitalService.getCapitalRows(sellerUser.getMemberId()))
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
        assertThrows(
            IllegalStateException.class, () -> contractService.create(sellerPerson, sampleCommand));
    assertEquals("Buyer would exceed concentration limit after transfer", thrown.getMessage());
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
            .transferAmounts(
                List.of(
                    new CapitalTransferAmount(
                        CAPITAL_PAYMENT,
                        new BigDecimal("10.0"),
                        new BigDecimal("5.0"),
                        new BigDecimal("1.0"))))
            .build();
    var sellerPerson = AuthenticatedPersonFixture.authenticatedPersonFromUser(sellerUser).build();

    when(userService.getById(sellerPerson.getUserId())).thenReturn(Optional.of(sellerUser));
    when(memberService.getById(sampleCommand.getBuyerMemberId()))
        .thenReturn(memberFixture().id(1L).user(buyerUser).build());

    IllegalArgumentException thrown =
        assertThrows(
            IllegalArgumentException.class,
            () -> contractService.create(sellerPerson, sampleCommand));
    assertEquals("Seller and buyer cannot be the same person.", thrown.getMessage());
  }

  @Test
  @DisplayName("Create capital transfer throws no amounts")
  void createNoAmounts() {

    var buyerUser = sampleUser().firstName("Olev").lastName("Ostja").build();
    var sellerUser = buyerUser;

    var sampleCommand =
        CreateCapitalTransferContractCommand.builder()
            .buyerMemberId(1L)
            .iban("TEST_IBAN")
            .transferAmounts(List.of())
            .build();
    var sellerPerson = AuthenticatedPersonFixture.authenticatedPersonFromUser(sellerUser).build();

    when(userService.getById(sellerPerson.getUserId())).thenReturn(Optional.of(sellerUser));
    when(memberService.getById(sampleCommand.getBuyerMemberId()))
        .thenReturn(memberFixture().id(1L).user(buyerUser).build());

    IllegalArgumentException thrown =
        assertThrows(
            IllegalArgumentException.class,
            () -> contractService.create(sellerPerson, sampleCommand));
    assertEquals("No amounts specified", thrown.getMessage());
  }

  @Test
  @DisplayName("Create capital transfer throws duplicate types")
  void createDuplicateTypes() {

    var buyerUser = sampleUser().firstName("Olev").lastName("Ostja").build();
    var sellerUser = buyerUser;

    var sampleCommand =
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
    var sellerPerson = AuthenticatedPersonFixture.authenticatedPersonFromUser(sellerUser).build();

    when(userService.getById(sellerPerson.getUserId())).thenReturn(Optional.of(sellerUser));
    when(memberService.getById(sampleCommand.getBuyerMemberId()))
        .thenReturn(memberFixture().id(1L).user(buyerUser).build());

    IllegalArgumentException thrown =
        assertThrows(
            IllegalArgumentException.class,
            () -> contractService.create(sellerPerson, sampleCommand));
    assertEquals("Duplicate types specified", thrown.getMessage());
  }

  @Test
  @DisplayName("Create capital transfer throws non-liquidatable types")
  void createNonLiquidatableTypes() {

    var buyerUser = sampleUser().firstName("Olev").lastName("Ostja").build();
    var sellerUser = buyerUser;

    var sampleCommand =
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
    var sellerPerson = AuthenticatedPersonFixture.authenticatedPersonFromUser(sellerUser).build();

    when(userService.getById(sellerPerson.getUserId())).thenReturn(Optional.of(sellerUser));
    when(memberService.getById(sampleCommand.getBuyerMemberId()))
        .thenReturn(memberFixture().id(1L).user(buyerUser).build());

    IllegalArgumentException thrown =
        assertThrows(
            IllegalArgumentException.class,
            () -> contractService.create(sellerPerson, sampleCommand));
    assertEquals("Non-liquidatable capital types included in command", thrown.getMessage());
  }

  @Test
  @DisplayName("Get capital being sold calculates totals")
  void getCapitalBeingSoldSummarizes() {

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

    var map = contractService.getCapitalBeingSoldInOtherTransfers(user.getMemberOrThrow());

    assertEquals(3, map.size());
    assertEquals(0, map.get(CAPITAL_PAYMENT).compareTo(new BigDecimal("200.00")));
    assertEquals(0, map.get(MEMBERSHIP_BONUS).compareTo(new BigDecimal("50.00")));
    assertEquals(0, map.get(WORK_COMPENSATION).compareTo(new BigDecimal("100.00")));
  }

  @Test
  @DisplayName("Get capital being bought calculates totals")
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

    var map = contractService.getCapitalBeingSoldInOtherTransfers(user.getMemberOrThrow());

    assertEquals(3, map.size());
    assertEquals(0, map.get(CAPITAL_PAYMENT).compareTo(new BigDecimal("200.00")));
    assertEquals(0, map.get(MEMBERSHIP_BONUS).compareTo(new BigDecimal("50.00")));
    assertEquals(0, map.get(WORK_COMPENSATION).compareTo(new BigDecimal("100.00")));
  }

  private ArgumentMatcher<ApplicationEvent> getStateChangeEventMatcher(
      User user, CapitalTransferContractState oldState, CapitalTransferContractState newState) {
    return event -> {
      var castEvent = (TrackableEvent) event;
      var data = castEvent.getData();
      return castEvent.getType() == CAPITAL_TRANSFER_STATE_CHANGE
          && castEvent.getPerson().equals(user)
          && data.get("oldState").equals(oldState)
          && data.get("newState").equals(newState);
    };
  }
}
