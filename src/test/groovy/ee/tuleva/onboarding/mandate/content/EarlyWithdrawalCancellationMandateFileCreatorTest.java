package ee.tuleva.onboarding.mandate.content;

import static ee.tuleva.onboarding.auth.UserFixture.sampleUser;
import static ee.tuleva.onboarding.epis.contact.ContactDetailsFixture.contactDetailsFixture;
import static ee.tuleva.onboarding.mandate.MandateFixture.sampleTransferCancellationMandate;
import static ee.tuleva.onboarding.mandate.application.ApplicationType.EARLY_WITHDRAWAL;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class EarlyWithdrawalCancellationMandateFileCreatorTest {

  @Mock private MandateContentService mandateContentService;

  @InjectMocks
  private EarlyWithdrawalCancellationMandateFileCreator
      earlyWithdrawalCancellationMandateFileCreator;

  @Test
  @DisplayName("creates early withdrawal cancellation mandate file")
  void test_delegatesToMandateContentCreator() {
    var aUser = sampleUser().build();
    var aContactDetails = contactDetailsFixture();
    var aMandate = sampleTransferCancellationMandate();

    when(mandateContentService.getMandateCancellationHtml(
            aUser, aMandate, aContactDetails, EARLY_WITHDRAWAL))
        .thenReturn("earlyWithdrawalCancellationContent");

    List<MandateContentFile> files =
        earlyWithdrawalCancellationMandateFileCreator.getContentFiles(
            aUser, aMandate, aContactDetails);

    MandateContentFile file = files.getFirst();
    assertThat(file.getName()).isEqualTo("avalduse_tyhistamise_avaldus_123.html");
    assertThat(file.getMimeType()).isEqualTo("text/html");
    assertThat(file.getContent()).isEqualTo("earlyWithdrawalCancellationContent".getBytes());
  }
}
