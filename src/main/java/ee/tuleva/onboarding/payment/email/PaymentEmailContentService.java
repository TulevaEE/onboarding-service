package ee.tuleva.onboarding.payment.email;

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
public class PaymentEmailContentService {
  private final TemplateEngine templateEngine;

  public String getThirdPillarPaymentSuccessHtml(User user, Locale locale) {
    Context ctx = new Context();
    ctx.setLocale(locale);
    ctx.setVariable("firstName", user.getFirstName());
    return templateEngine.process("third_pillar_payment_success_mandate", ctx);
  }

  public String getThirdPillarSuggestSecondHtml(User user, Locale locale) {
    Context ctx = new Context();
    ctx.setLocale(locale);
    ctx.setVariable("firstName", user.getFirstName());
    return templateEngine.process("third_pillar_suggest_second", ctx);
  }
}
