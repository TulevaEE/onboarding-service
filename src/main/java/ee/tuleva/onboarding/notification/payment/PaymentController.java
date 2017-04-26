package ee.tuleva.onboarding.notification.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.io.IOException;

@RestController
@AllArgsConstructor
@RequestMapping("/notifications")
public class PaymentController {

  private final ObjectMapper mapper;

  @PostMapping("/payments")
  public Payment incomingPayment(@ModelAttribute @Valid IncomingPayment incomingPayment) throws IOException {

    return mapper.readValue(incomingPayment.getJson(), Payment.class);
  }

}
