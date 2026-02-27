package ee.tuleva.onboarding.savings.fund;

import static ee.tuleva.onboarding.savings.fund.SavingFundPayment.Status.TO_BE_RETURNED;
import static ee.tuleva.onboarding.savings.fund.SavingFundPayment.Status.VERIFIED;
import static java.util.UUID.randomUUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import ee.tuleva.onboarding.ledger.SavingsFundLedger;
import ee.tuleva.onboarding.savings.fund.notification.UnattributedPaymentEvent;
import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.user.UserRepository;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class PaymentVerificationServiceTest {

  SavingFundPaymentRepository savingFundPaymentRepository = mock(SavingFundPaymentRepository.class);
  UserRepository userRepository = mock(UserRepository.class);
  SavingsFundOnboardingService savingsFundOnboardingService =
      mock(SavingsFundOnboardingService.class);
  SavingsFundLedger savingsFundLedger = mock(SavingsFundLedger.class);
  ApplicationEventPublisher applicationEventPublisher = mock(ApplicationEventPublisher.class);

  PaymentVerificationService service =
      new PaymentVerificationService(
          savingFundPaymentRepository,
          userRepository,
          savingsFundOnboardingService,
          savingsFundLedger,
          applicationEventPublisher,
          new NameMatcher());

  @Test
  void process_noPersonalCodeInDescription() {
    var payment = createPayment("37508295796", "my money");

    service.process(payment);

    verify(savingFundPaymentRepository).changeStatus(payment.getId(), TO_BE_RETURNED);
    verify(savingFundPaymentRepository)
        .addReturnReason(payment.getId(), "selgituses puudub isikukood");
    verify(savingsFundLedger).recordUnattributedPayment(payment.getAmount(), payment.getId());
    verify(applicationEventPublisher)
        .publishEvent(
            new UnattributedPaymentEvent(
                payment.getId(), payment.getAmount(), "selgituses puudub isikukood"));
    verifyNoMoreInteractions(savingFundPaymentRepository);
  }

  @Test
  void process_personalCodeMismatch() {
    var payment = createPayment("37508295796", "for user 45009144745");

    service.process(payment);

    verify(savingFundPaymentRepository).changeStatus(payment.getId(), TO_BE_RETURNED);
    verify(savingFundPaymentRepository)
        .addReturnReason(payment.getId(), "selgituses olev isikukood ei klapi maksja isikukoodiga");
    verify(savingsFundLedger).recordUnattributedPayment(payment.getAmount(), payment.getId());
    verify(applicationEventPublisher)
        .publishEvent(
            new UnattributedPaymentEvent(
                payment.getId(),
                payment.getAmount(),
                "selgituses olev isikukood ei klapi maksja isikukoodiga"));
    verifyNoMoreInteractions(savingFundPaymentRepository);
  }

  @Test
  void process_noSuchUser() {
    var payment = createPayment("37508295796", "to user 37508295796");
    when(userRepository.findByPersonalCode(any())).thenReturn(Optional.empty());

    service.process(payment);

    verify(userRepository).findByPersonalCode("37508295796");
    verify(savingFundPaymentRepository).changeStatus(payment.getId(), TO_BE_RETURNED);
    verify(savingFundPaymentRepository)
        .addReturnReason(payment.getId(), "isik ei ole Tuleva klient");
    verify(savingsFundLedger).recordUnattributedPayment(payment.getAmount(), payment.getId());
    verify(applicationEventPublisher)
        .publishEvent(
            new UnattributedPaymentEvent(
                payment.getId(), payment.getAmount(), "isik ei ole Tuleva klient"));
    verifyNoMoreInteractions(savingFundPaymentRepository);
  }

  @Test
  void process_notOnboarded() {
    var payment = createPayment("37508295796", "to user 37508295796");
    var user = User.builder().firstName("PÄRT").lastName("ÕLEKÕRS").build();
    when(userRepository.findByPersonalCode(any())).thenReturn(Optional.of(user));
    when(savingsFundOnboardingService.isOnboardingCompleted(any())).thenReturn(false);

    service.process(payment);

    verify(userRepository).findByPersonalCode("37508295796");
    verify(savingsFundOnboardingService).isOnboardingCompleted(user);
    verify(savingFundPaymentRepository).changeStatus(payment.getId(), TO_BE_RETURNED);
    verify(savingFundPaymentRepository)
        .addReturnReason(payment.getId(), "see isik ei ole täiendava kogumisfondiga liitunud");
    verify(savingsFundLedger).recordUnattributedPayment(payment.getAmount(), payment.getId());
    verify(applicationEventPublisher)
        .publishEvent(
            new UnattributedPaymentEvent(
                payment.getId(),
                payment.getAmount(),
                "see isik ei ole täiendava kogumisfondiga liitunud"));
    verifyNoMoreInteractions(savingFundPaymentRepository);
  }

  @Test
  void process_userNameMismatch_withoutRemitterIdCode() {
    var payment = createPayment(null, "to user 37508295796");
    var user = User.builder().firstName("PEETER").lastName("MEETER").build();
    when(userRepository.findByPersonalCode(any())).thenReturn(Optional.of(user));

    service.process(payment);

    verify(userRepository).findByPersonalCode("37508295796");
    verify(savingFundPaymentRepository).changeStatus(payment.getId(), TO_BE_RETURNED);
    verify(savingFundPaymentRepository)
        .addReturnReason(payment.getId(), "maksja nimi ei klapi Tuleva andmetega");
    verify(savingsFundLedger).recordUnattributedPayment(payment.getAmount(), payment.getId());
    verify(applicationEventPublisher)
        .publishEvent(
            new UnattributedPaymentEvent(
                payment.getId(), payment.getAmount(), "maksja nimi ei klapi Tuleva andmetega"));
    verifyNoMoreInteractions(savingFundPaymentRepository);
  }

  @Test
  void process_success() {
    var payment = createPayment("37508295796", "to user 37508295796");
    var user = User.builder().id(123L).firstName("PÄRT").lastName("ÕLEKÕRS").build();
    when(userRepository.findByPersonalCode(any())).thenReturn(Optional.of(user));
    when(savingsFundOnboardingService.isOnboardingCompleted(any())).thenReturn(true);

    service.process(payment);

    verify(userRepository).findByPersonalCode("37508295796");
    verify(savingsFundOnboardingService).isOnboardingCompleted(user);
    verify(savingsFundLedger).recordPaymentReceived(user, payment.getAmount(), payment.getId());
    var inOrder = inOrder(savingFundPaymentRepository);
    inOrder.verify(savingFundPaymentRepository).attachUser(payment.getId(), 123L);
    inOrder.verify(savingFundPaymentRepository).changeStatus(payment.getId(), VERIFIED);
    verifyNoMoreInteractions(savingFundPaymentRepository);
  }

  @Test
  void process_success_noRemitterIdCode() {
    var payment = createPayment(null, "to user 37508295796");
    var user = User.builder().id(444L).firstName("PÄRT").lastName("ÕLEKÕRS").build();
    when(userRepository.findByPersonalCode(any())).thenReturn(Optional.of(user));
    when(savingsFundOnboardingService.isOnboardingCompleted(any())).thenReturn(true);

    service.process(payment);

    verify(userRepository).findByPersonalCode("37508295796");
    verify(savingsFundOnboardingService).isOnboardingCompleted(user);
    verify(savingsFundLedger).recordPaymentReceived(user, payment.getAmount(), payment.getId());
    verify(savingFundPaymentRepository).changeStatus(payment.getId(), VERIFIED);
    verify(savingFundPaymentRepository).attachUser(payment.getId(), 444L);
    verifyNoMoreInteractions(savingFundPaymentRepository);
  }

  @Test
  void process_success_ignoreOtherValueInRemitterIdCode() {
    var payment = createPayment("P1234", "to user 37508295796");
    var user = User.builder().id(444L).firstName("PÄRT").lastName("ÕLEKÕRS").build();
    when(userRepository.findByPersonalCode(any())).thenReturn(Optional.of(user));
    when(savingsFundOnboardingService.isOnboardingCompleted(any())).thenReturn(true);

    service.process(payment);

    verify(userRepository).findByPersonalCode("37508295796");
    verify(savingsFundOnboardingService).isOnboardingCompleted(user);
    verify(savingsFundLedger).recordPaymentReceived(user, payment.getAmount(), payment.getId());
    verify(savingFundPaymentRepository).changeStatus(payment.getId(), VERIFIED);
    verify(savingFundPaymentRepository).attachUser(payment.getId(), 444L);
    verifyNoMoreInteractions(savingFundPaymentRepository);
  }

  @Test
  void process_success_allowNameMismatchIfRemitterIdCodeMatches() {
    var payment = createPayment("37508295796", "to user 37508295796");
    var user = User.builder().id(123L).firstName("KEEGI").lastName("TEINE").build();
    when(userRepository.findByPersonalCode(any())).thenReturn(Optional.of(user));
    when(savingsFundOnboardingService.isOnboardingCompleted(any())).thenReturn(true);

    service.process(payment);

    verify(userRepository).findByPersonalCode("37508295796");
    verify(savingsFundOnboardingService).isOnboardingCompleted(user);
    verify(savingsFundLedger).recordPaymentReceived(user, payment.getAmount(), payment.getId());
    verify(savingFundPaymentRepository).changeStatus(payment.getId(), VERIFIED);
    verify(savingFundPaymentRepository).attachUser(payment.getId(), 123L);
    verifyNoMoreInteractions(savingFundPaymentRepository);
  }

  @Test
  void extractPersonalCode() {
    assertThat(service.extractPersonalCode(null)).isEmpty();
    assertThat(service.extractPersonalCode("")).isEmpty();
    assertThat(service.extractPersonalCode("abc")).isEmpty();
    assertThat(service.extractPersonalCode("abc123")).isEmpty();
    assertThat(service.extractPersonalCode("1234567890")).isEmpty();
    assertThat(service.extractPersonalCode("12345678901")).isEmpty();
    assertThat(service.extractPersonalCode("45009144745")).contains("45009144745");
    assertThat(service.extractPersonalCode("37508295796")).contains("37508295796");
    assertThat(service.extractPersonalCode("37508295795"))
        .withFailMessage("invalid personal code not accepted")
        .isEmpty();
    assertThat(service.extractPersonalCode("375082957961"))
        .withFailMessage("additional digits not accepted")
        .isEmpty();
    assertThat(service.extractPersonalCode("137508295796"))
        .withFailMessage("additional digits not accepted")
        .isEmpty();
    assertThat(service.extractPersonalCode("some prefix 37508295796")).contains("37508295796");
    assertThat(service.extractPersonalCode("some prefix,37508295796,some suffix"))
        .contains("37508295796");
    assertThat(service.extractPersonalCode("some prefix+37508295796/some suffix,45009144745"))
        .contains("37508295796");
  }

  private SavingFundPayment createPayment(String remitterIdCode, String description) {
    return SavingFundPayment.builder()
        .id(randomUUID())
        .amount(new BigDecimal("100.00"))
        .remitterName("PÄRT ÕLEKÕRS")
        .remitterIban("EE123456789012345678")
        .remitterIdCode(remitterIdCode)
        .description(description)
        .build();
  }
}
