package ee.tuleva.onboarding.taxwin;

import java.util.Locale;
import java.util.Optional;

public interface ConsentServiceClient {

  ConsentRequest requestConsent(String personalCode, Locale locale);

  void approve(String confirmReference, byte[] signedContainer);

  Optional<String> findReference(String personalCode);
}
