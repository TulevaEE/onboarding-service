package ee.tuleva.onboarding.kyb;

import static ee.tuleva.onboarding.kyb.LegalForm.*;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class LegalFormTest {

  @Test
  void olesAccepted() {
    assertThat(OÜ.isAccepted()).isTrue();
  }

  @ParameterizedTest
  @EnumSource(
      value = LegalForm.class,
      names = {"OÜ"},
      mode = EnumSource.Mode.EXCLUDE)
  void otherFormsAreNotAccepted(LegalForm legalForm) {
    assertThat(legalForm.isAccepted()).isFalse();
  }

  @Test
  void fromStringParsesKnownValue() {
    assertThat(LegalForm.fromString("TÜH")).isEqualTo(TÜH);
  }

  @Test
  void fromStringReturnsOtherForUnknownValue() {
    assertThat(LegalForm.fromString("XYZZY")).isEqualTo(OTHER);
  }
}
