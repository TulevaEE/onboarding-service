package ee.tuleva.onboarding.locale;

import java.util.Arrays;
import java.util.Locale;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;

@Configuration
public class LocaleConfiguration {

  @Bean
  public LocaleResolver localeResolver() {
    AcceptHeaderLocaleResolver slr = new AcceptHeaderLocaleResolver();
    slr.setDefaultLocale(Locale.forLanguageTag("et"));
    slr.setSupportedLocales(Arrays.asList(Locale.forLanguageTag("et"), Locale.ENGLISH));
    return slr;
  }
}
