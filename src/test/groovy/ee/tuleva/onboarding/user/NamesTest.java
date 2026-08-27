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
    "mari, Mari",
    "mari-liis, Mari-Liis",
    "mari maasikas, Mari Maasikas",
    "McGregor, McGregor",
    "van der Berg, Van Der Berg",
  })
  void capitalizesNamePartsAndKeepsDeliberateCasing(String input, String expected) {
    assertThat(Names.formatted(input)).isEqualTo(expected);
  }

  @Test
  void keepsNullAndBlankAsTheyAre() {
    assertThat(Names.formatted(null)).isNull();
    assertThat(Names.formatted("")).isEmpty();
  }
}
