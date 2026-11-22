package ee.tuleva.onboarding.user;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AsciiLetterCaseTest {

  @Test
  void anAddressDifferingOnlyInAsciiLetterCaseIsTheSameAddress() {
    assertThat(AsciiLetterCase.equalIgnoringCase("Inbox@Example.COM", "inbox@example.com"))
        .isTrue();
  }

  @Test
  void aKelvinSignIsNotTheLetterK() {
    assertThat(AsciiLetterCase.equalIgnoringCase("\u212Aey@example.com", "key@example.com"))
        .isFalse();
  }

  @Test
  void aDotlessOrADottedCapitalIIsNotTheLetterI() {
    assertThat(AsciiLetterCase.equalIgnoringCase("pın@example.com", "pin@example.com")).isFalse();
    assertThat(AsciiLetterCase.equalIgnoringCase("İnbox@example.com", "inbox@example.com"))
        .isFalse();
  }

  @Test
  void aLongSIsNotTheLetterS() {
    assertThat(AsciiLetterCase.equalIgnoringCase("ſend@example.com", "send@example.com")).isFalse();
  }

  @Test
  void aMissingValueIsNeverTheSameAsAnother() {
    assertThat(AsciiLetterCase.equalIgnoringCase(null, "inbox@example.com")).isFalse();
    assertThat(AsciiLetterCase.equalIgnoringCase("inbox@example.com", null)).isFalse();
  }

  @Test
  void foldingLowersTheFirstAndLastAsciiCapitalsAndLeavesTheCharactersJustOutsideThem() {
    assertThat(AsciiLetterCase.fold("@AZ[")).isEqualTo("@az[");
  }

  @Test
  void foldingLowersOnlyTheAsciiLettersAndKeepsEveryOtherCharacterAsItIs() {
    assertThat(AsciiLetterCase.fold("\u212AEY.İN@ExÄmple.COM"))
        .isEqualTo("\u212Aey.İn@exÄmple.com");
  }
}
