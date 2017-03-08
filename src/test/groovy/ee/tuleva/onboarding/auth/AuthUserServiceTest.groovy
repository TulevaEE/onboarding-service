package ee.tuleva.onboarding.auth

import ee.tuleva.onboarding.user.User
import ee.tuleva.onboarding.user.UserRepository
import org.springframework.security.oauth2.common.exceptions.InvalidRequestException
import spock.lang.Specification

class AuthUserServiceTest extends Specification {

    UserRepository repository = Mock(UserRepository)
    AuthUserService service = new AuthUserService(repository)

    def "GetByPersonalCode fails when no such user exists in the repository"() {
        given:
        repository.findByPersonalCode("123") >> null

        when:
        service.getByPersonalCode("123")

        then:
        thrown InvalidRequestException
    }

    def "GetByPersonalCode fails when user is inactive"() {
        given:
        repository.findByPersonalCode("123") >> User.builder()
                .active(false)
                .build()

        when:
        service.getByPersonalCode("123")

        then:
        thrown InvalidRequestException
    }

    def "GetByPersonalCode works with an active user"() {
        given:
        def expectedUser = User.builder()
                .active(true)
                .build()
        repository.findByPersonalCode("123") >> expectedUser

        when:
        def user = service.getByPersonalCode("123")

        then:
        user == expectedUser
    }
}
