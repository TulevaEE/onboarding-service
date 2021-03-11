package ee.tuleva.onboarding.locale;

import java.util.Arrays;
import java.util.Locale;
import lombok.val;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;

@Configuration
public class LocaleConfiguration {

  public static final String DEFAULT_LANGUAGE = "et";

  @Bean
  public LocaleResolver localeResolver() {
    val defaultLocale = Locale.forLanguageTag(DEFAULT_LANGUAGE);
    AcceptHeaderLocaleResolver slr = new AcceptHeaderLocaleResolver();
    slr.setDefaultLocale(defaultLocale);
    slr.setSupportedLocales(Arrays.asList(defaultLocale, Locale.ENGLISH));
    return slr;
  }
}
