package ee.tuleva.onboarding.mandate.content;

import static ee.tuleva.onboarding.auth.UserFixture.sampleUser;
import static ee.tuleva.onboarding.mandate.MandateContactDetailsFixture.contactDetailsFixture;
import static ee.tuleva.onboarding.mandate.MandateFixture.sampleRejoinMandate;
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
class RejoinMandateFileCreatorTest {

  @Mock private MandateContentService mandateContentService;

  @InjectMocks private RejoinMandateFileCreator rejoinMandateFileCreator;

  @Test
  void namesTheSignedFileAfterTheApplicationForMakingContributions() {
    var user = sampleUser().build();
    var contactDetails = contactDetailsFixture();
    var mandate = sampleRejoinMandate();
    given(
            mandateContentService.getApplicantOnlyHtml(
                "second_pillar_rejoin", user, mandate, contactDetails))
        .willReturn("applicationForMakingContributionsContent");

    var files = rejoinMandateFileCreator.getContentFiles(user, mandate, contactDetails);

    assertThat(files)
        .usingRecursiveFieldByFieldElementComparator()
        .containsExactly(
            MandateContentFile.builder()
                .name("sissemaksete_tasumise_avaldus_123.html")
                .content("applicationForMakingContributionsContent".getBytes())
                .build());
  }

  @Test
  void supportsOnlyRejoins() {
    assertThat(rejoinMandateFileCreator.supports(REJOIN)).isTrue();
    assertThat(rejoinMandateFileCreator.supports(REJOIN_CANCELLATION)).isFalse();
  }
}
