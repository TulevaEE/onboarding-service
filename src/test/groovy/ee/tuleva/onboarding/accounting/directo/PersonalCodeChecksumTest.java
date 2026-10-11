package ee.tuleva.onboarding.accounting.directo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class PersonalCodeChecksumTest {

  private static final String PUBLISHED_TEST_IDENTITY = "38001085718";

  @Test
  void aCodePassingTheChecksumStopsTheEntity() {
    assertThatThrownBy(
            () -> PersonalCodeChecksum.stopIfAnyPasses(List.of("40015", PUBLISHED_TEST_IDENTITY)))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void codesFailingTheChecksumLetTheEntityRun() {
    assertThatCode(
            () ->
                PersonalCodeChecksum.stopIfAnyPasses(
                    List.of("40015", "38888888888", "E001", "TEAM1", "")))
        .doesNotThrowAnyException();
  }

  @Test
  void passesOnlyElevenDigitCodesWithAValidCheckDigit() {
    assertThat(PersonalCodeChecksum.passes(PUBLISHED_TEST_IDENTITY)).isTrue();
    assertThat(PersonalCodeChecksum.passes("38888888888")).isFalse();
    assertThat(PersonalCodeChecksum.passes("3800108571")).isFalse();
    assertThat(PersonalCodeChecksum.passes("380010857180")).isFalse();
    assertThat(PersonalCodeChecksum.passes("3800108571X")).isFalse();
  }

  @Test
  void aCodeOutsideTheCenturyDigitsOneToEightIsNoPersonalCode() {
    assertThat(PersonalCodeChecksum.passes("88001085712")).isTrue();
    assertThat(PersonalCodeChecksum.passes("08001085715")).isFalse();
    assertThat(PersonalCodeChecksum.passes("98001085713")).isFalse();
  }

  @Test
  void aFirstRoundRemainderOfTenFallsBackToTheSecondWeights() {
    assertThat(PersonalCodeChecksum.passes("70000000038")).isTrue();
    assertThat(PersonalCodeChecksum.passes("70000000030")).isFalse();
  }

  @Test
  void aSecondRoundRemainderOfTenMakesTheCheckDigitZero() {
    assertThat(PersonalCodeChecksum.passes("70000000390")).isTrue();
    assertThat(PersonalCodeChecksum.passes("70000000391")).isFalse();
  }
}
