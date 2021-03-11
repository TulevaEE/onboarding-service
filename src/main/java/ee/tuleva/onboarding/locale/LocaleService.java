package ee.tuleva.onboarding.locale;

import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LocaleService {

  public Locale getCurrentLocale() {
    return LocaleContextHolder.getLocale();
  }

  public String getLanguage() {
    return getCurrentLocale().getLanguage();
  }
}
