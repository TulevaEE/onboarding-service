package ee.tuleva.onboarding.mandate.generic;

import au.com.origin.snapshots.Expect;
import au.com.origin.snapshots.junit5.SnapshotExtension;
import ee.tuleva.onboarding.fund.FundRepository;
import ee.tuleva.onboarding.mandate.FundTransferExchange;
import ee.tuleva.onboarding.mandate.Mandate;
import ee.tuleva.onboarding.mandate.MandateType;
import ee.tuleva.onboarding.mandate.content.CompositeMandateFileCreator;
import ee.tuleva.onboarding.mandate.content.MandateContentFile;
import ee.tuleva.onboarding.mandate.content.MandateFileCreator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static ee.tuleva.onboarding.auth.UserFixture.sampleUser;
import static ee.tuleva.onboarding.epis.contact.ContactDetailsFixture.contactDetailsFixture;
import static ee.tuleva.onboarding.fund.FundFixture.lhv2ndPillarFund;
import static ee.tuleva.onboarding.fund.FundFixture.tuleva2ndPillarStockFund;
import static ee.tuleva.onboarding.mandate.MandateFixture.*;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest
@ExtendWith(SnapshotExtension.class)
public class GenericCompositeMandateIntegrationTest {

  @Autowired
  private List<MandateFileCreator> mandateFileCreators;
  @Autowired
  private List<MandateFactory> mandateFactories;

  @Test
  @DisplayName("all mandate types have required services")
  void testAllMandateTypesHaveSnapshotTest() {
    var allMandateTypes = MandateType.values();


    for (var mandateType : allMandateTypes) {
      var fileCreator = mandateFileCreators.stream().
          filter(mandateFileCreator -> mandateFileCreator.supports(mandateType))
          .findFirst()
          .orElse(null);


      if (fileCreator == null) {
        fail("No file creator for mandate type " + mandateType);
      }

      var mandateFactory = mandateFactories.stream()
          .filter(factory -> factory.supports(mandateType))
          .findFirst()
          .orElse(null);

      if (mandateFactory == null) {
        fail("No mandate factory for mandate type " + mandateType);
      }
    }
  }
}
