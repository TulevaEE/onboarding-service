package ee.tuleva.onboarding.mandate.content;

import static ee.tuleva.onboarding.auth.UserFixture.sampleUser;
import static ee.tuleva.onboarding.epis.contact.ContactDetailsFixture.contactDetailsFixture;
import static ee.tuleva.onboarding.fund.FundFixture.tuleva3rdPillarFund;
import static ee.tuleva.onboarding.mandate.MandateFixture.sampleMandate;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class CompositeMandateFileCreatorTest {

  @Mock private MandateFileCreator mandateFileCreator1;
  @Mock private MandateFileCreator mandateFileCreator2;

  private CompositeMandateFileCreator compositeMandateFileCreator;

  @BeforeEach
  void beforeEach() {
    mandateFileCreator1 = mock(EarlyWithdrawalCancellationMandateFileCreator.class);
    mandateFileCreator2 = mock(WithdrawalCancellationMandateFileCreator.class);

    compositeMandateFileCreator =
        new CompositeMandateFileCreator(List.of(mandateFileCreator1, mandateFileCreator2));
  }

  @Test
  @DisplayName("delegates file creation to mandate file creator")
  void test_delegatesToMandateContentCreator() {
    var aUser = sampleUser().build();
    var aContactDetails = contactDetailsFixture();
    var aMandate = sampleMandate();

    when(mandateFileCreator1.supports(aMandate.getMandateType())).thenReturn(false);
    when(mandateFileCreator2.supports(aMandate.getMandateType())).thenReturn(true);

    when(mandateFileCreator2.getContentFiles(aUser, aMandate, aContactDetails))
        .thenReturn(List.of(new MandateContentFile("test", "text/html", new byte[0])));

    List<MandateContentFile> files =
        compositeMandateFileCreator.getContentFiles(aUser, aMandate, aContactDetails);

    assertThat(files.getFirst().getName()).isEqualTo("test");
  }

  @Test
  @DisplayName("throws when no supported creators are found")
  void test_throwsNoSupportedMandateCreators() {
    var aUser = sampleUser().build();
    var aContactDetails = contactDetailsFixture();
    var aMandate = sampleMandate();
    var aFund = tuleva3rdPillarFund();

    when(mandateFileCreator1.supports(aMandate.getMandateType())).thenReturn(false);
    when(mandateFileCreator2.supports(aMandate.getMandateType())).thenReturn(false);

    IllegalStateException thrown =
        assertThrows(
            IllegalStateException.class,
            () -> compositeMandateFileCreator.getContentFiles(aUser, aMandate, aContactDetails),
            "Expected getContentFiles to throw, but didn't");

    assertThat(thrown.getMessage()).isEqualTo("Unsupported mandateType: UNKNOWN");
  }
}
