package ee.tuleva.onboarding.fund.manager;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class FundManagerTest {

  @Test
  void tulevaIsRecognizedCaseInsensitively() {
    assertThat(FundManager.builder().name("Tuleva").build().isTuleva()).isTrue();
    assertThat(FundManager.builder().name("tuleva").build().isTuleva()).isTrue();
    assertThat(FundManager.builder().name("TULEVA").build().isTuleva()).isTrue();
  }

  @Test
  void otherManagersAreNotTuleva() {
    assertThat(FundManager.builder().name("LHV").build().isTuleva()).isFalse();
    assertThat(FundManager.builder().name("Swedbank").build().isTuleva()).isFalse();
  }
}
