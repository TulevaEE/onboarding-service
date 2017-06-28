package ee.tuleva.onboarding.notification.mailchimp

import com.ecwid.maleorang.method.v3_0.lists.members.EditMemberMethod
import spock.lang.Specification

import static ee.tuleva.onboarding.auth.UserFixture.sampleUser

class MailChimpServiceSpec extends Specification {

	def mailChimpClient = Mock(MailChimpClientWrapper)
	def service = new MailChimpService(mailChimpClient)

	def setup() {
		service.listId = "someId"
	}

	def "creating or updating a Mailchimp member works"() {
		given:
		def user = sampleUser().build()

		when:
		service.createOrUpdateMember(user)

		then:
		mailChimpClient.execute(_) >> { EditMemberMethod.CreateOrUpdate method ->
			def mergeFields = method.merge_fields.mapping
			assert method.email_address == user.email
			assert mergeFields.FNAME == user.firstName
			assert mergeFields.LNAME == user.lastName
			assert mergeFields.ISIKUKOOD == user.personalCode
			assert mergeFields.TELEFON == user.phoneNumber
			assert mergeFields.LIIKME_NR == user.memberOrThrow.memberNumber
		}
	}
}
