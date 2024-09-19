package ee.tuleva.onboarding.mandate.content;

import static ee.tuleva.onboarding.auth.UserFixture.sampleUser;
import static ee.tuleva.onboarding.epis.contact.ContactDetailsFixture.contactDetailsFixture;
import static ee.tuleva.onboarding.fund.FundFixture.tuleva3rdPillarFund;
import static ee.tuleva.onboarding.mandate.MandateFixture.sampleMandate;
import static ee.tuleva.onboarding.mandate.MandateType.*;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import ee.tuleva.onboarding.fund.FundRepository;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class NonGenericMandateFileCreatorTest {

  @Mock private FundRepository fundRepository;

  @Mock private MandateContentCreator mandateContentCreator;

  @InjectMocks private NonGenericMandateFileCreator nonGenericMandateFileCreator;

  @Test
  @DisplayName("delegates file creation to mandate content creator")
  void test_delegatesToMandateContentCreator() {
    var aUser = sampleUser().build();
    var aContactDetails = contactDetailsFixture();
    var aMandate = sampleMandate();
    var aFund = tuleva3rdPillarFund();

    var aTestFileName = "test";

    when(fundRepository.findAllByPillarAndStatus(eq(aMandate.getPillar()), any()))
        .thenReturn(List.of(aFund));
    when(mandateContentCreator.getContentFiles(aUser, aMandate, List.of(aFund), aContactDetails))
        .thenReturn(List.of(new MandateContentFile(aTestFileName, new byte[0])));

    List<MandateContentFile> files =
        nonGenericMandateFileCreator.getContentFiles(aUser, aMandate, aContactDetails);
    assertThat(files.getFirst().getName()).isEqualTo(aTestFileName);
  }

  @Test
  @DisplayName("supports non generic mandates")
  void test_supportsTransferCancellationMandates() {
    assertThat(nonGenericMandateFileCreator.supports(UNKNOWN)).isTrue();
    assertThat(nonGenericMandateFileCreator.supports(WITHDRAWAL_CANCELLATION)).isFalse();
  }
}
