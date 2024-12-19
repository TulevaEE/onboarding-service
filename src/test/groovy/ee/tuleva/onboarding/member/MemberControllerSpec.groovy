package ee.tuleva.onboarding.member

import ee.tuleva.onboarding.BaseControllerSpec
import ee.tuleva.onboarding.user.member.Member
import ee.tuleva.onboarding.user.member.MemberRepository
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

class MemberControllerSpec extends BaseControllerSpec {

    MemberRepository memberRepository = Mock(MemberRepository)
    MemberController controller = new MemberController(memberRepository)

    def "Head: Gets total member count"() {
        given:
        def mvc = mockMvc(controller)
        when:
        def performCall = mvc
        .perform(MockMvcRequestBuilders.head("/v1/members"))

        then:
        1 * memberRepository.findAll() >> [new Member()]
        performCall
                .andExpect(status().isOk())
                .andExpect(header().longValue("x-total-count", 1))

    }
}
