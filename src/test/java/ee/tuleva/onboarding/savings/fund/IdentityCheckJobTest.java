package ee.tuleva.onboarding.savings.fund;

import static java.util.UUID.randomUUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.user.UserRepository;

@ExtendWith(MockitoExtension.class)
class IdentityCheckJobTest {

  @Mock IdentityCheckRepository identityCheckRepository;
  @Mock UserRepository userRepository;
  @Mock SavingsFundOnboardingService savingsFundOnboardingService;
  @InjectMocks IdentityCheckJob job;

  @Test
  void process_noPersonalCodeInDescription() {
    var payment = createPayment("37508295796", "my money");

    job.process(payment);

    verify(identityCheckRepository).identityCheckFailure(payment, "selgituses puudub isikukood");
    verifyNoMoreInteractions(identityCheckRepository);
  }

  @Test
  void process_personalCodeMismatch() {
    var payment = createPayment("37508295796", "for user 45009144745");

    job.process(payment);

    verify(identityCheckRepository).identityCheckFailure(payment, "selgituses olev isikukood ei klapi maksja isikukoodiga");
    verifyNoMoreInteractions(identityCheckRepository);
  }

  @Test
  void process_noSuchUser() {
    var payment = createPayment("37508295796", "to user 37508295796");
    when(userRepository.findByPersonalCode(any())).thenReturn(Optional.empty());

    job.process(payment);

    verify(userRepository).findByPersonalCode("37508295796");
    verify(identityCheckRepository).identityCheckFailure(payment, "isik ei ole Tuleva klient");
    verifyNoMoreInteractions(identityCheckRepository);
  }

  @Test
  void process_notOnboarded() {
    var payment = createPayment("37508295796", "to user 37508295796");
    var user = User.builder().firstName("PÄRT").lastName("ÕLEKÕRS").build();
    when(userRepository.findByPersonalCode(any())).thenReturn(Optional.of(user));
    when(savingsFundOnboardingService.isOnboardingCompleted(any())).thenReturn(false);

    job.process(payment);

    verify(userRepository).findByPersonalCode("37508295796");
    verify(savingsFundOnboardingService).isOnboardingCompleted(user);
    verify(identityCheckRepository).identityCheckFailure(payment, "see isik ei ole täiendava kogumisfondiga liitunud");
    verifyNoMoreInteractions(identityCheckRepository);
  }

  @Test
  void process_userNameMismatch_withoutRemitterIdCode() {
    var payment = createPayment(null, "to user 37508295796");
    var user = User.builder().firstName("PEETER").lastName("MEETER").build();
    when(userRepository.findByPersonalCode(any())).thenReturn(Optional.of(user));

    job.process(payment);

    verify(userRepository).findByPersonalCode("37508295796");
    verify(identityCheckRepository).identityCheckFailure(payment, "maksja nimi ei klapi Tuleva andmetega");
    verifyNoMoreInteractions(identityCheckRepository);
  }

  @Test
  void process_success() {
    var payment = createPayment("37508295796", "to user 37508295796");
    var user = User.builder().firstName("PÄRT").lastName("ÕLEKÕRS").build();
    when(userRepository.findByPersonalCode(any())).thenReturn(Optional.of(user));
    when(savingsFundOnboardingService.isOnboardingCompleted(any())).thenReturn(true);

    job.process(payment);

    verify(userRepository).findByPersonalCode("37508295796");
    verify(savingsFundOnboardingService).isOnboardingCompleted(user);
    verify(identityCheckRepository).identityCheckSuccess(payment, user);
    verifyNoMoreInteractions(identityCheckRepository);
  }

  @Test
  void process_success_noRemitterIdCode() {
    var payment = createPayment(null, "to user 37508295796");
    var user = User.builder().firstName("PÄRT").lastName("ÕLEKÕRS").build();
    when(userRepository.findByPersonalCode(any())).thenReturn(Optional.of(user));
    when(savingsFundOnboardingService.isOnboardingCompleted(any())).thenReturn(true);

    job.process(payment);

    verify(userRepository).findByPersonalCode("37508295796");
    verify(savingsFundOnboardingService).isOnboardingCompleted(user);
    verify(identityCheckRepository).identityCheckSuccess(payment, user);
    verifyNoMoreInteractions(identityCheckRepository);
  }

  @Test
  void process_success_allowNameMismatchIfRemitterIdCodeMatches() {
    var payment = createPayment("37508295796", "to user 37508295796");
    var user = User.builder().firstName("KEEGI").lastName("TEINE").build();
    when(userRepository.findByPersonalCode(any())).thenReturn(Optional.of(user));
    when(savingsFundOnboardingService.isOnboardingCompleted(any())).thenReturn(true);

    job.process(payment);

    verify(userRepository).findByPersonalCode("37508295796");
    verify(savingsFundOnboardingService).isOnboardingCompleted(user);
    verify(identityCheckRepository).identityCheckSuccess(payment, user);
    verifyNoMoreInteractions(identityCheckRepository);
  }

  private SavingFundPayment createPayment(String remitterIdCode, String description) {
    return SavingFundPayment.builder()
        .id(randomUUID())
        .remitterName("PÄRT ÕLEKÕRS")
        .remitterIdCode(remitterIdCode)
        .description(description)
        .build();
  }

  @Test
  void isSameName() {
    assertThat(job.isSameName(null, null)).isFalse();
    assertThat(job.isSameName("A", null)).isFalse();
    assertThat(job.isSameName(null, "A")).isFalse();
    assertThat(job.isSameName("A", "B")).isFalse();
    assertThat(job.isSameName("A", "A")).isTrue();
    assertThat(job.isSameName("A", " A   ")).isTrue();
    assertThat(job.isSameName("A A", "AA")).isFalse();
    assertThat(job.isSameName("AA BB CC", "BB CC AA")).isTrue();
    assertThat(job.isSameName("AA/BB,CC", "BB+CC-AA")).isTrue();
    assertThat(job.isSameName("aaa bbb", "Aaa BBB")).isTrue();
  }

  @Test
  void extractPersonalCode() {
    assertThat(job.extractPersonalCode(null)).isEmpty();
    assertThat(job.extractPersonalCode("")).isEmpty();
    assertThat(job.extractPersonalCode("abc")).isEmpty();
    assertThat(job.extractPersonalCode("abc123")).isEmpty();
    assertThat(job.extractPersonalCode("1234567890")).isEmpty();
    assertThat(job.extractPersonalCode("12345678901")).isEmpty();
    assertThat(job.extractPersonalCode("45009144745")).contains("45009144745");
    assertThat(job.extractPersonalCode("37508295796")).contains("37508295796");
    assertThat(job.extractPersonalCode("37508295795")).withFailMessage("invalid personal code not accepted").isEmpty();
    assertThat(job.extractPersonalCode("375082957961")).withFailMessage("additional digits not accepted").isEmpty();
    assertThat(job.extractPersonalCode("137508295796")).withFailMessage("additional digits not accepted").isEmpty();
    assertThat(job.extractPersonalCode("some prefix 37508295796")).contains("37508295796");
    assertThat(job.extractPersonalCode("some prefix,37508295796,some suffix")).contains("37508295796");
    assertThat(job.extractPersonalCode("some prefix+37508295796/some suffix,45009144745")).contains("37508295796");
  }

}
