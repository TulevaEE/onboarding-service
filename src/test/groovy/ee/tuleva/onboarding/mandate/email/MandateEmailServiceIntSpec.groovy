package ee.tuleva.onboarding.mandate.email

import ee.tuleva.onboarding.mandate.Mandate
import ee.tuleva.onboarding.user.User
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import spock.lang.Ignore
import spock.lang.Specification

import static ee.tuleva.onboarding.auth.UserFixture.sampleUser
import static ee.tuleva.onboarding.mandate.MandateFixture.sampleTransferCancellationMandate
import static ee.tuleva.onboarding.mandate.MandateFixture.sampleWithdrawalCancellationMandate
import static ee.tuleva.onboarding.mandate.MandateFixture.sampleMandate
import static ee.tuleva.onboarding.mandate.MandateFixture.thirdPillarMandate
import static ee.tuleva.onboarding.mandate.email.PillarSuggestionFixture.secondPillarSuggestion
import static ee.tuleva.onboarding.mandate.email.PillarSuggestionFixture.thirdPillarSuggestion

@SpringBootTest
@Ignore
class MandateEmailServiceIntSpec extends Specification {

  @Autowired
  MandateEmailService mandateEmailService

  def "send 2nd pillar mandate"() {
    given:
    User user = sampleUser().email("erko@risthein.ee").build()
    Mandate mandate = sampleMandate()
    PillarSuggestion pillarSuggestion = secondPillarSuggestion

    when:
    mandateEmailService.sendMandate(user, mandate, pillarSuggestion, Locale.ENGLISH)
    mandateEmailService.sendMandate(user, mandate, pillarSuggestion, new Locale("et"))

    then:
    true
  }

  def "send 3rd pillar mandate"() {
    given:
    User user = sampleUser().id(1).email("erko@risthein.ee").build()
    Mandate mandate = thirdPillarMandate().tap { it.id = 1 }
    PillarSuggestion pillarSuggestion = thirdPillarSuggestion

    when:
    mandateEmailService.sendMandate(user, mandate, pillarSuggestion, Locale.ENGLISH)
    mandateEmailService.sendMandate(user, mandate, pillarSuggestion, new Locale("et"))

    then:
    true
  }

  def "send withdrawal cancellation mandate"() {
    given:
    User user = sampleUser().id(1).email("erko@risthein.ee").build()
    Mandate mandate = sampleWithdrawalCancellationMandate()
    PillarSuggestion pillarSuggestion = secondPillarSuggestion

    when:
    mandateEmailService.sendMandate(user, mandate, pillarSuggestion, Locale.ENGLISH)
    mandateEmailService.sendMandate(user, mandate, pillarSuggestion, new Locale("et"))

    then:
    true
  }

  def "send transfer cancellation mandate"() {
    given:
    User user = sampleUser().id(1).email("erko@risthein.ee").build()
    Mandate mandate = sampleTransferCancellationMandate()
    PillarSuggestion pillarSuggestion = secondPillarSuggestion

    when:
    mandateEmailService.sendMandate(user, mandate, pillarSuggestion, Locale.ENGLISH)
    mandateEmailService.sendMandate(user, mandate, pillarSuggestion, new Locale("et"))

    then:
    true
  }

  def "ScheduleThirdPillarSuggestSecondEmail"() {
    given:
    User user = sampleUser().id(1).email("erko@risthein.ee").build()

    when:
    mandateEmailService.scheduleThirdPillarSuggestSecondEmail(user, Locale.ENGLISH)
    mandateEmailService.scheduleThirdPillarSuggestSecondEmail(user, new Locale("et"))

    then:
    true
  }
}
