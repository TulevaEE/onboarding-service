package ee.tuleva.onboarding.notification.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import ee.tuleva.onboarding.error.ValidationErrorsException;
import ee.tuleva.onboarding.notification.email.EmailService;
import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.user.UserService;
import java.io.IOException;
import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.validation.Errors;
import org.springframework.validation.SmartValidator;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import springfox.documentation.annotations.ApiIgnore;

@RestController
@RequiredArgsConstructor
@Slf4j
@RequestMapping("/notifications")
public class PaymentController {

  private static final String COMPLETED = "COMPLETED";

  private final ObjectMapper mapper;
  private final UserService userService;
  private final SmartValidator validator;
  private final EmailService emailService;

  @Value("${membership-success.url}")
  private String membershipSuccessUrl;

  @PostMapping("/payments")
  public void incomingPayment(
      @ModelAttribute @Valid IncomingPayment incomingPayment,
      @ApiIgnore HttpServletResponse response,
      @ApiIgnore Errors errors)
      throws IOException, ValidationErrorsException {

    log.info("Incoming payment: {}", incomingPayment);

    Payment payment = mapper.readValue(incomingPayment.getJson(), Payment.class);

    validator.validate(payment, errors);
    if (errors.hasErrors()) {
      throw new ValidationErrorsException(errors);
    }

    Long userId = payment.getReference();

    boolean isAMember = userService.isAMember(userId);
    boolean isStatusCompleted = COMPLETED.equalsIgnoreCase(payment.getStatus());

    if (isStatusCompleted && !isAMember) {
      User user = userService.registerAsMember(userId, payment.getCustomerName());
      emailService.sendMemberNumber(user);
    } else {
      log.warn(
          "Invalid incoming payment. Status: {}, user is a member: {}",
          payment.getStatus(),
          isAMember);
    }

    response.sendRedirect(membershipSuccessUrl);
  }
}
