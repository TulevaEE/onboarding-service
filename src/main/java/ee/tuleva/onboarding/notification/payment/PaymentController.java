package ee.tuleva.onboarding.notification.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import ee.tuleva.onboarding.user.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;
import java.io.IOException;

@RestController
@RequiredArgsConstructor
@Slf4j
@RequestMapping("/notifications")
public class PaymentController {

  private static final String COMPLETED = "COMPLETED";

  private final ObjectMapper mapper;
  private final UserService userService;

  @Value("${frontend.url}")
  private String frontendUrl;

  @PostMapping("/payments")
  public void incomingPayment(@ModelAttribute @Valid IncomingPayment incomingPayment,
                              HttpServletResponse response) throws IOException {

    log.info("Incoming payment: {}", incomingPayment);

    Payment payment = mapper.readValue(incomingPayment.getJson(), Payment.class);

    Long userId = payment.getReference();

    if (COMPLETED.equalsIgnoreCase(payment.getStatus()) && !userService.isAMember(userId)) {
      userService.registerAsMember(userId);
      response.sendRedirect(frontendUrl + "/steps/select-sources?isNewMember=true");
    }

  }

}
