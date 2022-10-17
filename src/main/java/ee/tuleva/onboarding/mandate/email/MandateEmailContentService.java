package ee.tuleva.onboarding.mandate.email;

import static java.time.format.DateTimeFormatter.ofPattern;

import ee.tuleva.onboarding.deadline.MandateDeadlines;
import ee.tuleva.onboarding.deadline.MandateDeadlinesService;
import ee.tuleva.onboarding.fund.FundRepository;
import ee.tuleva.onboarding.mandate.Mandate;
import ee.tuleva.onboarding.user.User;
import java.time.Instant;
import java.time.LocalDate;
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
  private final MandateDeadlinesService mandateDeadlinesService;
  private final FundRepository fundRepository;

  public String getSecondPillarHtml(
      User user, Instant mandateDate, PillarSuggestion thirdPillarSuggestion, Locale locale) {
    Context ctx = new Context();
    ctx.setLocale(locale);
    ctx.setVariable("firstName", user.getFirstName());
    ctx.setVariable("thirdPillarSuggestion", thirdPillarSuggestion);

    MandateDeadlines deadlines = mandateDeadlinesService.getDeadlines(mandateDate);
    LocalDate transferDate = deadlines.getTransferMandateFulfillmentDate();
    ctx.setVariable("transferDate", transferDate.format(ofPattern("dd.MM.yyyy")));

    return templateEngine.process("second_pillar_mandate", ctx);
  }

  public String getSecondPillarTransferCancellationHtml(User user, Mandate mandate, Locale locale) {
    Context ctx = new Context();
    ctx.setLocale(locale);
    ctx.setVariable("firstName", user.getFirstName());

    String sourceFundIsin = mandate.getFundTransferExchanges().get(0).getSourceFundIsin();
    String sourceFundName = fundRepository.findByIsin(sourceFundIsin).getName(locale);
    ctx.setVariable("sourceFundName", sourceFundName);

    return templateEngine.process("second_pillar_transfer_cancellation_email", ctx);
  }

  public String getSecondPillarWithdrawalCancellationHtml(User user, Locale locale) {
    Context ctx = new Context();
    ctx.setLocale(locale);
    ctx.setVariable("firstName", user.getFirstName());
    return templateEngine.process("second_pillar_withdrawal_cancellation_email", ctx);
  }

  public String getThirdPillarPaymentReminderHtml(User user, Locale locale) {
    Context ctx = new Context();
    ctx.setLocale(locale);
    ctx.setVariable("firstName", user.getFirstName());
    return templateEngine.process("third_pillar_payment_reminder_mandate", ctx);
  }
  public String getThirdPillarSuggestSecondHtml(User user, Locale locale) {
    Context ctx = new Context();
    ctx.setLocale(locale);
    ctx.setVariable("firstName", user.getFirstName());
    return templateEngine.process("third_pillar_suggest_second", ctx);
  }

}
