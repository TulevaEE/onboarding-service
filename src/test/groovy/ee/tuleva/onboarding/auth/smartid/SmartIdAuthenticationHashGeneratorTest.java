package ee.tuleva.onboarding.auth.smartid;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SmartIdAuthenticationHashGeneratorTest {

  @Test
  void generateHashReturnsAUsableRandomHash() {
    var generator = new SmartIdAuthenticationHashGenerator();

    var hash = generator.generateHash();

    assertThat(hash).isNotNull();
    assertThat(hash.calculateVerificationCode()).isNotBlank();
  }

  @Test
  void generateHashReturnsADifferentHashEachTime() {
    var generator = new SmartIdAuthenticationHashGenerator();

    var first = generator.generateHash();
    var second = generator.generateHash();

    assertThat(first.getHashInBase64()).isNotEqualTo(second.getHashInBase64());
  }
}
