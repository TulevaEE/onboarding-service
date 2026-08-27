package ee.tuleva.onboarding.user;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class NamesTest {

  @ParameterizedTest
  @CsvSource({
    "MARI, Mari",
    "MARI-LIIS, Mari-Liis",
    "ANNA MARIA, Anna Maria",
    "VÄLI-TAMM, Väli-Tamm",
    "ÕNNELA, Õnnela",
    "O'BRIEN, O'Brien",
    "Mari, Mari",
    "Mari-Liis, Mari-Liis",
    "McGregor, McGregor",
    "van der Berg, van der Berg",
  })
  void formatsOnlyAllCapsNames(String input, String expected) {
    assertThat(Names.formatted(input)).isEqualTo(expected);
  }

  @Test
  void keepsNullAndBlankAsTheyAre() {
    assertThat(Names.formatted(null)).isNull();
    assertThat(Names.formatted("")).isEmpty();
  }
}
