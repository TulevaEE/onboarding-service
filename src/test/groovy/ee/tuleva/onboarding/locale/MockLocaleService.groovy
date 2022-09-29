package ee.tuleva.onboarding.locale

class MockLocaleService extends LocaleService {

  private Locale locale = Locale.ENGLISH

  MockLocaleService() {
  }

  MockLocaleService(String language) {
    this.locale = Locale.forLanguageTag(language);
  }

  @Override
  Locale getCurrentLocale() {
    return locale
  }

}
