package ee.tuleva.onboarding.savings.fund;

import static ee.tuleva.onboarding.savings.fund.SavingFundPayment.Status.TO_BE_RETURNED;
import static ee.tuleva.onboarding.savings.fund.SavingFundPayment.Status.VERIFIED;
import static java.util.UUID.randomUUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.user.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PaymentVerificationServiceTest {

  @Mock SavingFundPaymentRepository savingFundPaymentRepository;
  @Mock UserRepository userRepository;
  @Mock SavingsFundOnboardingService savingsFundOnboardingService;
  @InjectMocks PaymentVerificationService service;

  @Test
  void process_noPersonalCodeInDescription() {
    var payment = createPayment("37508295796", "my money");

    service.process(payment);

    verify(savingFundPaymentRepository).changeStatus(payment.getId(), TO_BE_RETURNED);
    verify(savingFundPaymentRepository)
        .addReturnReason(payment.getId(), "selgituses puudub isikukood");
    verifyNoMoreInteractions(savingFundPaymentRepository);
  }

  @Test
  void process_personalCodeMismatch() {
    var payment = createPayment("37508295796", "for user 45009144745");

    service.process(payment);

    verify(savingFundPaymentRepository).changeStatus(payment.getId(), TO_BE_RETURNED);
    verify(savingFundPaymentRepository)
        .addReturnReason(payment.getId(), "selgituses olev isikukood ei klapi maksja isikukoodiga");
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
    verifyNoMoreInteractions(savingFundPaymentRepository);
  }

  @Test
  void process_success() {
    var payment = createPayment("37508295796", "to user 37508295796");
    var user = User.builder().firstName("PÄRT").lastName("ÕLEKÕRS").build();
    when(userRepository.findByPersonalCode(any())).thenReturn(Optional.of(user));
    when(savingsFundOnboardingService.isOnboardingCompleted(any())).thenReturn(true);

    service.process(payment);

    verify(userRepository).findByPersonalCode("37508295796");
    verify(savingsFundOnboardingService).isOnboardingCompleted(user);
    verify(savingFundPaymentRepository).changeStatus(payment.getId(), VERIFIED);
    verifyNoMoreInteractions(savingFundPaymentRepository);
  }

  @Test
  void process_success_noRemitterIdCode() {
    var payment = createPayment(null, "to user 37508295796");
    var user = User.builder().firstName("PÄRT").lastName("ÕLEKÕRS").build();
    when(userRepository.findByPersonalCode(any())).thenReturn(Optional.of(user));
    when(savingsFundOnboardingService.isOnboardingCompleted(any())).thenReturn(true);

    service.process(payment);

    verify(userRepository).findByPersonalCode("37508295796");
    verify(savingsFundOnboardingService).isOnboardingCompleted(user);
    verify(savingFundPaymentRepository).changeStatus(payment.getId(), VERIFIED);
    verifyNoMoreInteractions(savingFundPaymentRepository);
  }

  @Test
  void process_success_allowNameMismatchIfRemitterIdCodeMatches() {
    var payment = createPayment("37508295796", "to user 37508295796");
    var user = User.builder().firstName("KEEGI").lastName("TEINE").build();
    when(userRepository.findByPersonalCode(any())).thenReturn(Optional.of(user));
    when(savingsFundOnboardingService.isOnboardingCompleted(any())).thenReturn(true);

    service.process(payment);

    verify(userRepository).findByPersonalCode("37508295796");
    verify(savingsFundOnboardingService).isOnboardingCompleted(user);
    verify(savingFundPaymentRepository).changeStatus(payment.getId(), VERIFIED);
    verifyNoMoreInteractions(savingFundPaymentRepository);
  }

  @Test
  void isSameName() {
    assertThat(service.isSameName(null, null)).isFalse();
    assertThat(service.isSameName("A", null)).isFalse();
    assertThat(service.isSameName(null, "A")).isFalse();
    assertThat(service.isSameName("A", "B")).isFalse();
    assertThat(service.isSameName("A", "A")).isTrue();
    assertThat(service.isSameName("A", " A   ")).isTrue();
    assertThat(service.isSameName("A A", "AA")).isFalse();
    assertThat(service.isSameName("AA BB CC", "BB CC AA")).isTrue();
    assertThat(service.isSameName("AA/BB,CC", "BB+CC-AA")).isTrue();
    assertThat(service.isSameName("aaa bbb", "Aaa BBB")).isTrue();
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
        .remitterName("PÄRT ÕLEKÕRS")
        .remitterIdCode(remitterIdCode)
        .description(description)
        .build();
  }
}
