package ee.tuleva.onboarding.user;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.epis.EpisService;
import ee.tuleva.onboarding.epis.contact.ContactDetailsService;
import ee.tuleva.onboarding.epis.contact.UserPreferences;
import ee.tuleva.onboarding.error.ValidationErrorsException;
import ee.tuleva.onboarding.user.command.UpdateUserCommand;
import ee.tuleva.onboarding.user.response.UserResponse;
import io.swagger.annotations.ApiOperation;
import javax.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.Errors;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import springfox.documentation.annotations.ApiIgnore;

@RestController
@RequestMapping("/v1")
@AllArgsConstructor
public class UserController {

  private final UserService userService;
  private final EpisService episService;
  private final ContactDetailsService contactDetailsService;

  @ApiOperation(value = "Get info about the current user")
  @GetMapping("/me")
  public UserResponse me(
      @ApiIgnore @AuthenticationPrincipal AuthenticatedPerson authenticatedPerson) {
    Long userId = authenticatedPerson.getUserId();
    User user = userService.getById(userId);
    UserPreferences contactDetails = episService.getContactDetails(authenticatedPerson);
    return UserResponse.from(user, contactDetails);
  }

  @GetMapping("/me/principal")
  public Person getPrincipal(
      @ApiIgnore @AuthenticationPrincipal AuthenticatedPerson authenticatedPerson) {
    return authenticatedPerson;
  }

  @ApiOperation(value = "Update the current user")
  @PatchMapping("/me")
  public UserResponse patchMe(
      @ApiIgnore @AuthenticationPrincipal AuthenticatedPerson authenticatedPerson,
      @Valid @RequestBody UpdateUserCommand cmd,
      @ApiIgnore Errors errors) {

    if (errors != null && errors.hasErrors()) {
      throw new ValidationErrorsException(errors);
    }

    User user =
        userService.updateUser(
            authenticatedPerson.getPersonalCode(), cmd.getEmail(), cmd.getPhoneNumber());

    if (cmd.getAddress() != null) {
      UserPreferences contactDetails =
          contactDetailsService.updateContactDetails(user, cmd.getAddress());
      return UserResponse.from(user, contactDetails);
    }

    return UserResponse.from(user);
  }

}
