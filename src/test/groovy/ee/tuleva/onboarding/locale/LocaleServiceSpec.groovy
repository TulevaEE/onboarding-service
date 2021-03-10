package ee.tuleva.onboarding.locale

import org.springframework.context.i18n.LocaleContextHolder
import spock.lang.Specification

class LocaleServiceSpec extends Specification {

  LocaleService localeService = new LocaleService()

  def "returns locale from context"() {
    given:
    Locale locale = Locale.CANADA
    LocaleContextHolder.setLocale(locale)

    when:
    Locale fromService = localeService.currentLocale

    then:
    locale == fromService
  }
}
