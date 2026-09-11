package ee.tuleva.onboarding.locale;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.LocaleResolver;

class LocaleConfigurationTest {

  private final LocaleConfiguration localeConfiguration = new LocaleConfiguration();

  @Test
  void localeResolverFallsBackToTheEstonianDefaultLocaleWhenNoAcceptLanguageIsSent() {
    LocaleResolver localeResolver = localeConfiguration.localeResolver();
    MockHttpServletRequest request = new MockHttpServletRequest();

    assertThat(localeResolver.resolveLocale(request)).isEqualTo(LocaleConfiguration.DEFAULT_LOCALE);
  }

  @Test
  void localeResolverFallsBackToPlainEnglishForAnUnsupportedEnglishRegion() {
    LocaleResolver localeResolver = localeConfiguration.localeResolver();
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addPreferredLocale(Locale.US);

    assertThat(localeResolver.resolveLocale(request)).isEqualTo(Locale.ENGLISH);
  }
}
