package ee.tuleva.onboarding.mandate.email;

import static ee.tuleva.onboarding.auth.UserFixture.sampleUserNonMember;
import static ee.tuleva.onboarding.mandate.MandateFixture.sampleFundPensionOpeningMandate;
import static ee.tuleva.onboarding.mandate.MandateFixture.sampleMandateWithPaymentRate;
import static ee.tuleva.onboarding.mandate.MandateFixture.samplePartialWithdrawalMandate;
import static ee.tuleva.onboarding.mandate.MandateFixture.sampleRejoinMandate;
import static ee.tuleva.onboarding.mandate.MandateFixture.sampleSelectionMandate;
import static ee.tuleva.onboarding.mandate.batch.MandateBatchFixture.aSavedMandateBatch;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;

import ee.tuleva.onboarding.mandate.Mandate;
import ee.tuleva.onboarding.mandate.event.MandateBatchSignatureStored;
import ee.tuleva.onboarding.notification.email.PreviousContactNotices;
import ee.tuleva.onboarding.user.User;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PreviousContactNoticeSenderTest {

  @Mock PreviousContactNotices previousContactNotices;

  @InjectMocks PreviousContactNoticeSender sender;

  private static final Instant SIGNED_AT = Instant.parse("2026-11-21T09:00:00Z");

  private final User user = sampleUserNonMember().build();

  @Test
  void aSignedWithdrawalBatchIsNoticedInTheLanguageItWasSignedIn() {
    sender.sendWithdrawalNotice(
        signed(List.of(samplePartialWithdrawalMandate(), sampleFundPensionOpeningMandate())));

    then(previousContactNotices).should().sendActivityNotice(user, Locale.ENGLISH, SIGNED_AT);
  }

  @Test
  void aSignedRejoinBatchIsNotNoticed() {
    sender.sendWithdrawalNotice(
        signed(
            List.of(
                sampleRejoinMandate(), sampleMandateWithPaymentRate(), sampleSelectionMandate())));

    then(previousContactNotices).shouldHaveNoInteractions();
  }

  @Test
  void aSignedBatchThatPadsAWithdrawalWithAnotherApplicationIsStillNoticed() {
    sender.sendWithdrawalNotice(
        signed(List.of(samplePartialWithdrawalMandate(), sampleMandateWithPaymentRate())));

    then(previousContactNotices).should().sendActivityNotice(user, Locale.ENGLISH, SIGNED_AT);
  }

  @Test
  void aFailingNoticeFailsNeitherTheSigningNorTheSubmissionToPensionikeskus() {
    willThrow(new IllegalStateException())
        .given(previousContactNotices)
        .sendActivityNotice(user, Locale.ENGLISH, SIGNED_AT);

    assertThatCode(
            () -> sender.sendWithdrawalNotice(signed(List.of(samplePartialWithdrawalMandate()))))
        .doesNotThrowAnyException();
  }

  private MandateBatchSignatureStored signed(List<Mandate> mandates) {
    return new MandateBatchSignatureStored(
        user, aSavedMandateBatch(mandates), Locale.ENGLISH, SIGNED_AT);
  }
}
