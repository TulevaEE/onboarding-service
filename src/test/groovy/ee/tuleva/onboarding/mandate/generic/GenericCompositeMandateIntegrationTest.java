package ee.tuleva.onboarding.mandate.generic;

import static ee.tuleva.onboarding.mandate.MandateType.REJOIN_CANCELLATION;
import static ee.tuleva.onboarding.mandate.MandateType.UNKNOWN;
import static org.assertj.core.api.Assertions.assertThat;

import au.com.origin.snapshots.junit5.SnapshotExtension;
import ee.tuleva.onboarding.mandate.content.MandateFileCreator;
import java.util.EnumSet;
import java.util.List;
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
  void everyMandateTypeHasAFileCreator() {
    assertThat(EnumSet.complementOf(EnumSet.of(UNKNOWN)))
        .allSatisfy(
            mandateType ->
                assertThat(mandateFileCreators)
                    .anyMatch(fileCreator -> fileCreator.supports(mandateType)));
  }

  @Test
  void
      everyMandateTypeHasAMandateFactoryButTheRejoinCancellationOnlyCancelingAPendingRejoinFiles() {
    assertThat(EnumSet.complementOf(EnumSet.of(UNKNOWN, REJOIN_CANCELLATION)))
        .allSatisfy(
            mandateType ->
                assertThat(mandateFactories).anyMatch(factory -> factory.supports(mandateType)));
  }
}
