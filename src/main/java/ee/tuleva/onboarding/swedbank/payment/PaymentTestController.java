package ee.tuleva.onboarding.swedbank.payment;

import static ee.tuleva.onboarding.swedbank.fetcher.SwedbankStatementFetcher.SwedbankAccount.DEPOSIT_EUR;

import java.math.BigDecimal;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ee.tuleva.onboarding.swedbank.fetcher.SwedbankAccountConfiguration;
import ee.tuleva.onboarding.swedbank.http.SwedbankGatewayClient;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/payment-test")
@RequiredArgsConstructor
public class PaymentTestController {

  private final SwedbankGatewayClient swedbankGatewayClient;
  private final SwedbankAccountConfiguration swedbankAccountConfiguration;

  @GetMapping()
  public void send() {
    var requestId = UUID.randomUUID();
    swedbankGatewayClient.sendPaymentRequest(PaymentRequest.builder()
            .remitterId("14118923")
            .remitterName("Tuleva Fondid AS")
            .remitterIban(swedbankAccountConfiguration.getAccountIban(DEPOSIT_EUR).orElseThrow())
            .remitterBic("HABAEE2X")
            .beneficiaryName("Erik Jõgi")
            .beneficiaryIban("EE387700771001281238")
            .ourId(requestId.toString().replace("-", ""))
            .endToEndId(requestId.toString().replace("-", ""))
            .description("test payment")
            .amount(new BigDecimal("1.23"))
            .build(),
        requestId);
  }

}
