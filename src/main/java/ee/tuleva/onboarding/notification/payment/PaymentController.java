package ee.tuleva.onboarding.notification.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import ee.tuleva.onboarding.user.UserService;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;
import java.io.IOException;

@RestController
@AllArgsConstructor
@RequestMapping("/notifications")
public class PaymentController {

  private final ObjectMapper mapper;
  private final UserService userService;

  @PostMapping("/payments")
  public void incomingPayment(@ModelAttribute @Valid IncomingPayment incomingPayment,
                                HttpServletResponse response) throws IOException {

    Payment payment = mapper.readValue(incomingPayment.getJson(), Payment.class);

    Long userId = payment.getReference();
    userService.registerAsMember(userId);

    response.sendRedirect("http://localhost:3000/steps/select-sources?isNewMember=true");
  }

}
