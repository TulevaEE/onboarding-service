package ee.tuleva.onboarding.mandate.generic;

import static ee.tuleva.onboarding.mandate.MandateFixture.*;
import static ee.tuleva.onboarding.mandate.MandateType.UNKNOWN;
import static org.junit.jupiter.api.Assertions.fail;

import au.com.origin.snapshots.junit5.SnapshotExtension;
import ee.tuleva.onboarding.mandate.MandateType;
import ee.tuleva.onboarding.mandate.content.MandateFileCreator;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@ExtendWith(SnapshotExtension.class)
public class GenericCompositeMandateIntegrationTest {

  @Autowired private List<MandateFileCreator> mandateFileCreators;
  @Autowired private List<MandateFactory> mandateFactories;

  @Test
  @DisplayName("all mandate types have required services")
  void testAllMandateTypesHaveSnapshotTest() {
    var allMandateTypes = MandateType.values();

    for (var mandateType : allMandateTypes) {
      if (mandateType == UNKNOWN) {
        continue;
      }
      var fileCreator =
          mandateFileCreators.stream()
              .filter(mandateFileCreator -> mandateFileCreator.supports(mandateType))
              .findFirst()
              .orElse(null);

      if (fileCreator == null) {
        fail("No file creator for mandate type " + mandateType);
      }

      var mandateFactory =
          mandateFactories.stream()
              .filter(factory -> factory.supports(mandateType))
              .findFirst()
              .orElse(null);

      if (mandateFactory == null) {
        fail("No mandate factory for mandate type " + mandateType);
      }
    }
  }
}
