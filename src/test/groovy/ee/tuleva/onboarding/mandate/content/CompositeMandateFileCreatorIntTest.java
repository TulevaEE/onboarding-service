package ee.tuleva.onboarding.mandate.content;

import static ee.tuleva.onboarding.auth.UserFixture.sampleUser;
import static ee.tuleva.onboarding.epis.contact.ContactDetailsFixture.contactDetailsFixture;
import static ee.tuleva.onboarding.fund.FundFixture.*;
import static ee.tuleva.onboarding.mandate.MandateFixture.*;
import static ee.tuleva.onboarding.mandate.MandateType.UNKNOWN;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import au.com.origin.snapshots.Expect;
import au.com.origin.snapshots.junit5.SnapshotExtension;
import ee.tuleva.onboarding.fund.FundRepository;
import ee.tuleva.onboarding.mandate.FundTransferExchange;
import ee.tuleva.onboarding.mandate.Mandate;
import ee.tuleva.onboarding.mandate.MandateType;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@ExtendWith(SnapshotExtension.class)
public class CompositeMandateFileCreatorIntTest {
  @Autowired private CompositeMandateFileCreator compositeMandateFileCreator;
  @MockitoBean private FundRepository fundRepository;
  private Expect expect;

  static Stream<Arguments> testMandatesWithSnapshotName() {
    return Stream.of(
        Arguments.of(sampleWithdrawalCancellationMandate(), "WithdrawalCancellationMandate"),
        Arguments.of(
            sampleEarlyWithdrawalCancellationMandate(), "EarlyWithdrawalCancellationMandate"),
        Arguments.of(sampleFundPensionOpeningMandate(), "FundPensionOpeningMandate"),
        Arguments.of(sampleTransferCancellationMandate(), "TransferCancellationMandate"),
        Arguments.of(sampleFundPensionOpeningMandate(), "SecondPillarFundPensionOpeningMandate"),
        Arguments.of(
            sampleFundPensionOpeningMandate(aThirdPillarFundPensionOpeningMandateDetails),
            "ThirdPillarFundPensionOpeningMandate"),
        Arguments.of(samplePartialWithdrawalMandate(), "SecondPillarPartialWithdrawalMandate"),
        Arguments.of(
            samplePartialWithdrawalMandate(aThirdPillarPartialWithdrawalMandateDetails),
            "ThirdPillarPartialWithdrawalMandate"),
        Arguments.of(sampleMandateWithPaymentRate(), "PaymentRateChangeMandate"),
        Arguments.of(sampleSelectionMandate(), "SampleSelectionMandate"));
  }

  void writeMandateFile(MandateContentFile file, String snapshotName) {

    Path snapshotDirectory =
        Paths.get("src/test/groovy/ee/tuleva/onboarding/mandate/content/__snapshots__");

    Path filePath = snapshotDirectory.resolve(snapshotName + "_" + file.getName());
    try (FileOutputStream outputStream = new FileOutputStream(filePath.toFile())) {
      outputStream.write(file.getContent());
    } catch (IOException e) {
      throw new RuntimeException(e.getMessage());
    }
  }

  @Test
  void testAllMandateTypesHaveSnapshotTest() {
    var allMandateTypes = MandateType.values();

    var testMandateTypes =
        testMandatesWithSnapshotName()
            .map(arguments -> ((Mandate) arguments.get()[0]))
            .map(Mandate::getMandateType)
            .collect(Collectors.toSet());

    for (var mandateType : allMandateTypes) {
      if (mandateType == UNKNOWN) {
        continue;
      }
      if (!testMandateTypes.contains(mandateType)) {
        fail("No file creator test for mandate type " + mandateType);
      }
    }
  }

  @ParameterizedTest
  @MethodSource("testMandatesWithSnapshotName")
  void testMandatesSnapshot(Mandate aMandate, String snapshotName) {
    var anUser = sampleUser().build();
    var anContactDetails = contactDetailsFixture();

    var aFund = tuleva2ndPillarStockFund();
    var aFund2 = lhv2ndPillarFund();
    when(fundRepository.findAllByPillarAndStatus(any(), any())).thenReturn(List.of(aFund, aFund2));

    List<MandateContentFile> files =
        compositeMandateFileCreator.getContentFiles(anUser, aMandate, anContactDetails);

    assertThat(files.size()).isEqualTo(1);

    for (MandateContentFile file : files) {
      expect.scenario(snapshotName).toMatchSnapshot(new String(file.getContent(), UTF_8));
      writeMandateFile(file, snapshotName);
    }
  }

  @Test
  @DisplayName("test transfer cancellation")
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
