package ee.tuleva.onboarding.mandate.content;

import static ee.tuleva.onboarding.auth.UserFixture.sampleUser;
import static ee.tuleva.onboarding.mandate.MandateContactDetailsFixture.contactDetailsFixture;
import static ee.tuleva.onboarding.mandate.MandateFixture.sampleRejoinCancellationMandate;
import static ee.tuleva.onboarding.mandate.MandateType.REJOIN;
import static ee.tuleva.onboarding.mandate.MandateType.REJOIN_CANCELLATION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RejoinCancellationMandateFileCreatorTest {

  @Mock private MandateContentService mandateContentService;

  @InjectMocks private RejoinCancellationMandateFileCreator rejoinCancellationMandateFileCreator;

  @Test
  void namesTheSignedFileAfterTheLeaveApplicationItFiles() {
    var user = sampleUser().build();
    var contactDetails = contactDetailsFixture();
    var mandate = sampleRejoinCancellationMandate();
    given(
            mandateContentService.getApplicantOnlyHtml(
                "second_pillar_rejoin_cancellation", user, mandate, contactDetails))
        .willReturn("leaveApplicationContent");

    var files = rejoinCancellationMandateFileCreator.getContentFiles(user, mandate, contactDetails);

    assertThat(files)
        .usingRecursiveFieldByFieldElementComparator()
        .containsExactly(
            MandateContentFile.builder()
                .name("maksete_mittetegemise_avaldus_123.html")
                .content("leaveApplicationContent".getBytes())
                .build());
  }

  @Test
  void supportsOnlyRejoinCancellations() {
    assertThat(rejoinCancellationMandateFileCreator.supports(REJOIN_CANCELLATION)).isTrue();
    assertThat(rejoinCancellationMandateFileCreator.supports(REJOIN)).isFalse();
  }
}
