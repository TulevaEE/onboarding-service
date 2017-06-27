package ee.tuleva.onboarding.notification.mailchimp

import com.ecwid.maleorang.method.v3_0.lists.members.EditMemberMethod
import com.ecwid.maleorang.method.v3_0.lists.members.MemberInfo
import org.junit.Ignore
import spock.lang.Specification

class MailChimpServiceSpec extends Specification {

	def mailChimpClient = Mock(MailChimpClientWrapper)
//	def mailChimpClient = new MailchimpClient("c978d114acb93617bd7c6f378ea3b52c-us15")
	def service = new MailChimpService(mailChimpClient)

	def setup() {
		service.listId = "45dbcd7e96"
	}

	@Ignore
	def "Name"() {
		given:
		def mailChimpMember = MailChimpMember.builder()
				.email("erko@risthein.ee")
				.firstName("Erko")
				.lastName("Risthein")
				.build()
		def expectedMemberInfo = new MemberInfo()

		when:
		def memberInfo = service.createOrUpdateMember(mailChimpMember)

		then:
		mailChimpClient.execute(_) >> { EditMemberMethod.CreateOrUpdate method ->
			def mergeFields = method.merge_fields.mapping
			assert method.email_address == "erko@risthein.ee"
			assert mergeFields.FNAME == "Erko"
			assert mergeFields.LNAME == "Risthein"
			return expectedMemberInfo
		}
		memberInfo == expectedMemberInfo
	}
}
