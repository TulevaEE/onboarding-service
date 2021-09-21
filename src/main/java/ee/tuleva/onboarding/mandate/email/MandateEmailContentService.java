package ee.tuleva.onboarding.mandate.email;

import ee.tuleva.onboarding.mandate.Mandate;
import ee.tuleva.onboarding.user.User;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

@Service
@RequiredArgsConstructor
@Slf4j
public class MandateEmailContentService {

  private final TemplateEngine templateEngine;

  public String getSecondPillarHtml(
      User user, PillarSuggestion thirdPillarSuggestion, Locale locale) {
    Context ctx = new Context();
    ctx.setLocale(locale);
    ctx.setVariable("firstName", user.getFirstName());
    ctx.setVariable("thirdPillarSuggestion", thirdPillarSuggestion);
    return templateEngine.process("second_pillar_mandate", ctx);
  }

  public String getThirdPillarPaymentDetailsHtml(
      User user,
      PillarSuggestion secondPillarSuggestion,
      String pensionAccountNumber,
      Locale locale) {
    Context ctx = new Context();
    ctx.setLocale(locale);
    ctx.setVariable("firstName", user.getFirstName());
    ctx.setVariable("secondPillarSuggestion", secondPillarSuggestion);
    ctx.setVariable("pensionAccountNumber", pensionAccountNumber);
    return templateEngine.process("third_pillar_mandate_payment_details", ctx);
  }

  public String getSecondPillarTransferCancellationHtml(User user, Mandate mandate, Locale locale) {
    Context ctx = new Context();
    ctx.setLocale(locale);
    ctx.setVariable("firstName", user.getFirstName());
    ctx.setVariable(
        "sourceFundName", mandate.getFundTransferExchanges().get(0).getSourceFundIsin());
    return templateEngine.process("second_pillar_transfer_cancellation_email", ctx);
  }

  public String getSecondPillarWithdrawalCancellationHtml(User user, Locale locale) {
    Context ctx = new Context();
    ctx.setLocale(locale);
    ctx.setVariable("firstName", user.getFirstName());
    return templateEngine.process("second_pillar_withdrawal_cancellation_email", ctx);
  }
}
