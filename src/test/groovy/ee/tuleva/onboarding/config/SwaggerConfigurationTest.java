package ee.tuleva.onboarding.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SwaggerConfigurationTest {

  private final SwaggerConfiguration configuration = new SwaggerConfiguration();

  @Test
  void accountIdentityApiExposesTulevaOnboardingServiceInfo() {
    var openApi = configuration.accountIdentityApi();

    assertThat(openApi).isNotNull();
    assertThat(openApi.getInfo().getTitle()).isEqualTo("Tuleva onboarding service");
    assertThat(openApi.getInfo().getVersion()).isEqualTo("1.0");
    assertThat(openApi.getInfo().getContact().getName()).isEqualTo("Tuleva");
    assertThat(openApi.getInfo().getContact().getUrl()).isEqualTo("https://github.com/TulevaEE");
  }
}
