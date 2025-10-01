package ee.tuleva.onboarding.payment.savings;

import com.nimbusds.jose.JWSObject;
import ee.tuleva.onboarding.payment.PaymentData;
import ee.tuleva.onboarding.payment.provider.montonio.MontonioOrderToken;
import ee.tuleva.onboarding.payment.provider.montonio.MontonioTokenParser;
import ee.tuleva.onboarding.savings.fund.SavingFundPayment;
import ee.tuleva.onboarding.savings.fund.SavingFundPaymentRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class SavingsCallbackService {

  private final MontonioTokenParser tokenParser;
  private final SavingsChannelConfiguration savingsChannelConfiguration;
  private final SavingFundPaymentRepository savingFundPaymentRepository;

  @SneakyThrows
  public Optional<SavingFundPayment> processToken(String serializedToken) {
    var jwsObject = JWSObject.parse(serializedToken);
    tokenParser.verifyToken(jwsObject, savingsChannelConfiguration.getSecretKey());
    var token = tokenParser.parse(jwsObject);

    if (!token.getPaymentStatus().equals(MontonioOrderToken.MontonioOrderStatus.PAID)) {
      log.info("Montonio order {} not paid", token.getMerchantReference());
      return Optional.empty();
    }

    if (!token.getMerchantReference().getPaymentType().equals(PaymentData.PaymentType.SAVINGS)) {
      log.error("Montonio order {} not SAVINGS type", token.getMerchantReference());
      return Optional.empty();
    }

    if (savingFundPaymentRepository.existsByRemitterIdCodeAndDescription(
        token.getMerchantReference().getRecipientPersonalCode(),
        token.getMerchantReference().getDescription())) {
      log.info("Saving fund payment already exists for {}", token.getMerchantReference());
      return Optional.empty();
    }

    var payment =
        SavingFundPayment.builder()
            .remitterName(token.getSenderName())
            .remitterIban(token.getSenderIban())
            .remitterIdCode(token.getMerchantReference().getRecipientPersonalCode())
            .description(token.getMerchantReference().getDescription())
            .amount(token.getGrandTotal())
            .currency(token.getCurrency())
            .build();
    savingFundPaymentRepository.save(payment);
    return Optional.of(payment);
  }
}
