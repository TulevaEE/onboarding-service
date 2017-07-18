package ee.tuleva.onboarding.user

import ee.tuleva.onboarding.auth.UserFixture
import ee.tuleva.onboarding.user.command.CreateUserCommand
import ee.tuleva.onboarding.user.response.UserResponse
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import spock.lang.Specification

@SpringBootTest
public class UserCreationIntegrationSpec extends Specification {

    @Autowired
    UserService userService
    @Autowired
    UserController userController

    def "handles non member user re-registering"() {
        given:
        User userToCreateFirst = UserFixture.sampleUserNonMember()
                .build()

        when:
        User newUser = userService.createNewUser(userToCreateFirst)
        CreateUserCommand createUserCommand = new CreateUserCommand()
        createUserCommand.setEmail(userToCreateFirst.email)
        createUserCommand.setPersonalCode(userToCreateFirst.personalCode)
        createUserCommand.setPhoneNumber(userToCreateFirst.phoneNumber)
        UserResponse userResponse = userController.createUser(createUserCommand, null)

        then:
        newUser != null
        userResponse.personalCode == newUser.personalCode


    }

}
