package ee.tuleva.onboarding.testsupport;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.TimeZone;
import org.junit.jupiter.api.Test;

class TestJvmTimeZoneTest {

  @Test
  void testsRunInUtcWhateverTheMachinesZoneSoH2CachesUtcWhicheverTestOpensItFirst() {
    assertThat(TimeZone.getDefault().getID()).isEqualTo("UTC");
  }
}
