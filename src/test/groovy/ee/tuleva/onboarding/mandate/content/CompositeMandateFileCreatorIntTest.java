package ee.tuleva.onboarding.mandate.content;

import static ee.tuleva.onboarding.auth.UserFixture.sampleUser;
import static ee.tuleva.onboarding.epis.contact.ContactDetailsFixture.contactDetailsFixture;
import static ee.tuleva.onboarding.fund.FundFixture.*;
import static ee.tuleva.onboarding.mandate.MandateFixture.*;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import au.com.origin.snapshots.Expect;
import au.com.origin.snapshots.junit5.SnapshotExtension;
import ee.tuleva.onboarding.fund.FundRepository;
import ee.tuleva.onboarding.mandate.FundTransferExchange;
import ee.tuleva.onboarding.mandate.Mandate;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

@SpringBootTest
@ExtendWith(SnapshotExtension.class)
public class CompositeMandateFileCreatorIntTest {
  @Autowired private CompositeMandateFileCreator compositeMandateFileCreator;
  @MockBean private FundRepository fundRepository;
  private Expect expect;

  static Stream<Arguments> testMandatesWithSnapshotName() {
    return Stream.of(
        Arguments.of(sampleWithdrawalCancellationMandate(), "WithdrawalCancellationMandate"),
        Arguments.of(
            sampleEarlyWithdrawalCancellationMandate(), "EarlyWithdrawalCancellationMandate"),
        Arguments.of(sampleFundPensionOpeningMandate(), "FundPensionOpeningMandate"),
        Arguments.of(sampleFundPensionOpeningMandate(), "SecondPillarFundPensionOpeningMandate"),
        Arguments.of(
            sampleFundPensionOpeningMandate(aThirdPillarFundPensionOpeningMandateDetails),
            "ThirdPillarFundPensionOpeningMandate"));
  }

  void writeMandateFile(MandateContentFile file, String snapshotName) {

    Path snapshotDirectory =
        Paths.get("src/test/groovy/ee/tuleva/onboarding/mandate/content/__snapshots__");

    Path filePath = snapshotDirectory.resolve(snapshotName + "_" + file.getName());
    System.out.println(filePath);
    try (FileOutputStream outputStream = new FileOutputStream(filePath.toFile())) {
      outputStream.write(file.getContent());
    } catch (IOException e) {
      throw new RuntimeException(e.getMessage());
    }
  }

  @ParameterizedTest
  @MethodSource("testMandatesWithSnapshotName")
  void testMandatesSnapshot(Mandate aMandate, String snapshotName) {
    var anUser = sampleUser().build();
    var anContactDetails = contactDetailsFixture();

    List<MandateContentFile> files =
        compositeMandateFileCreator.getContentFiles(anUser, aMandate, anContactDetails);

    assertThat(files.size()).isEqualTo(1);

    for (MandateContentFile file : files) {
      expect.scenario(snapshotName).toMatchSnapshot(new String(file.getContent(), UTF_8));
      writeMandateFile(file, snapshotName);
    }
  }

  @Test
  void testTransferCancellationMandate() {
    var aMandate = sampleTransferCancellationMandate();
    var anUser = sampleUser().build();
    var anContactDetails = contactDetailsFixture();

    var aFund = tuleva2ndPillarStockFund();
    var aFund2 = lhv2ndPillarFund();

    aMandate.setFundTransferExchanges(
        List.of(
            FundTransferExchange.builder()
                .id(1234L)
                .sourceFundIsin(aFund.getIsin())
                .targetFundIsin(null)
                .amount(null)
                .build()));

    when(fundRepository.findAllByPillarAndStatus(any(), any())).thenReturn(List.of(aFund, aFund2));

    List<MandateContentFile> files =
        compositeMandateFileCreator.getContentFiles(anUser, aMandate, anContactDetails);

    assertThat(files.size()).isEqualTo(1);

    for (MandateContentFile file : files) {
      expect.toMatchSnapshot(new String(file.getContent(), UTF_8));
      writeMandateFile(file, "TransferCancellationMandate");
    }
  }
}
