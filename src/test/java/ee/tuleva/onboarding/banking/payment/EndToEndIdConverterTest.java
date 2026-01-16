package ee.tuleva.onboarding.banking.payment;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EndToEndIdConverterTest {

  private final EndToEndIdConverter converter = new EndToEndIdConverter();

  @Test
  @DisplayName("toEndToEndId removes dashes from UUID")
  void toEndToEndId_removesDashes() {
    var uuid = UUID.fromString("12345678-1234-5678-1234-567812345678");

    var result = converter.toEndToEndId(uuid);

    assertThat(result).isEqualTo("12345678123456781234567812345678");
    assertThat(result).hasSize(32);
  }

  @Test
  @DisplayName("toUuid adds dashes back to 32-char string")
  void toUuid_addsDashes() {
    var endToEndId = "12345678123456781234567812345678";

    var result = converter.toUuid(endToEndId);

    assertThat(result).contains(UUID.fromString("12345678-1234-5678-1234-567812345678"));
  }

  @Test
  @DisplayName("toEndToEndId and toUuid are reversible")
  void toEndToEndId_and_toUuid_areReversible() {
    var uuid = UUID.randomUUID();

    var endToEndId = converter.toEndToEndId(uuid);
    var result = converter.toUuid(endToEndId);

    assertThat(result).contains(uuid);
  }

  @Test
  @DisplayName("toUuid returns empty for null input")
  void toUuid_returnsEmptyForNull() {
    var result = converter.toUuid(null);

    assertThat(result).isEmpty();
  }

  @Test
  @DisplayName("toUuid returns empty for string with wrong length")
  void toUuid_returnsEmptyForWrongLength() {
    var result = converter.toUuid("12345");

    assertThat(result).isEmpty();
  }

  @Test
  @DisplayName("toUuid returns empty for invalid UUID characters")
  void toUuid_returnsEmptyForInvalidCharacters() {
    var result = converter.toUuid("zzzzzzzz123456781234567812345678");

    assertThat(result).isEmpty();
  }
}
