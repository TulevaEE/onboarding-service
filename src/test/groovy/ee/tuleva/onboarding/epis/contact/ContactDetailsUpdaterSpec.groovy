package ee.tuleva.onboarding.epis.contact

import ee.tuleva.onboarding.mandate.event.AfterMandateSignedEvent
import spock.lang.Specification

import static ee.tuleva.onboarding.auth.UserFixture.sampleUser
import static ee.tuleva.onboarding.mandate.MandateFixture.sampleMandate
import static java.util.Locale.ENGLISH

class ContactDetailsUpdaterSpec extends Specification {

    ContactDetailsService contactDetailsService = Mock()
    ContactDetailsUpdater contactDetailsUpdater = new ContactDetailsUpdater(contactDetailsService)

    def "updates user address after mandate is created"() {
        given:
        def user = sampleUser().build()
        def mandate = sampleMandate()
        def locale = ENGLISH
        def event = new AfterMandateSignedEvent(user, mandate, locale)

        when:
        contactDetailsUpdater.updateAddress(event)

        then:
        1 * contactDetailsService.updateContactDetails(user, mandate.address)
    }
}
