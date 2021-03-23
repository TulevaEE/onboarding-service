package ee.tuleva.onboarding.locale;

import java.util.Arrays;
import java.util.Locale;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;

@Configuration
public class LocaleConfiguration {

  public static final String DEFAULT_LANGUAGE = "et";
  public static final Locale DEFAULT_LOCALE = Locale.forLanguageTag(DEFAULT_LANGUAGE);

  @Bean
  public LocaleResolver localeResolver() {
    AcceptHeaderLocaleResolver localeResolver = new AcceptHeaderLocaleResolver();
    localeResolver.setDefaultLocale(DEFAULT_LOCALE);
    localeResolver.setSupportedLocales(Arrays.asList(DEFAULT_LOCALE, Locale.ENGLISH));
    return localeResolver;
  }
}
