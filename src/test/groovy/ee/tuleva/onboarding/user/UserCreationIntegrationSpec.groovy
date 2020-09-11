package ee.tuleva.onboarding.user

import ee.tuleva.onboarding.epis.EpisService
import ee.tuleva.onboarding.user.command.CreateUserCommand
import ee.tuleva.onboarding.user.response.UserResponse
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import spock.lang.Specification

import static ee.tuleva.onboarding.auth.UserFixture.sampleUserNonMember
import static ee.tuleva.onboarding.epis.contact.ContactDetailsFixture.contactDetailsFixture
import static org.mockito.Mockito.when

@SpringBootTest
public class UserCreationIntegrationSpec extends Specification {

    @Autowired
    UserService userService
    @Autowired
    UserController userController
    @MockBean
    EpisService episService

    def "handles non member user re-registering"() {
        given:
        User userToCreateFirst = sampleUserNonMember().build()
        when(episService.getContactDetails(userToCreateFirst)).thenReturn(contactDetailsFixture())

        when:
        User newUser = userService.createNewUser(userToCreateFirst)
        CreateUserCommand createUserCommand = new CreateUserCommand(
            email: userToCreateFirst.email,
            personalCode: userToCreateFirst.personalCode,
            phoneNumber: userToCreateFirst.phoneNumber
        )
        UserResponse userResponse = userController.createUser(createUserCommand, null)

        then:
        newUser != null
        userResponse.personalCode == newUser.personalCode


    }

}
