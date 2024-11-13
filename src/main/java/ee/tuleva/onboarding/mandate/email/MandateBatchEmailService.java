package ee.tuleva.onboarding.mandate.email;

import static ee.tuleva.onboarding.mandate.email.EmailVariablesAttachments.*;

import com.microtripit.mandrillapp.lutung.view.MandrillMessage;
import ee.tuleva.onboarding.auth.principal.AuthenticationHolder;
import ee.tuleva.onboarding.deadline.MandateDeadlinesService;
import ee.tuleva.onboarding.fund.FundRepository;
import ee.tuleva.onboarding.mandate.batch.MandateBatch;
import ee.tuleva.onboarding.mandate.email.persistence.EmailPersistenceService;
import ee.tuleva.onboarding.mandate.email.persistence.EmailType;
import ee.tuleva.onboarding.notification.email.EmailService;
import ee.tuleva.onboarding.paymentrate.SecondPillarPaymentRateService;
import ee.tuleva.onboarding.user.User;
import java.time.Clock;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class MandateBatchEmailService {

  private final EmailService emailService;
  private final EmailPersistenceService emailPersistenceService;
  private final Clock clock;
  private final FundRepository fundRepository;
  private final MandateDeadlinesService mandateDeadlinesService;
  private final SecondPillarPaymentRateService secondPillarPaymentRateService;
  private final AuthenticationHolder authenticationHolder;

  public void sendMandateBatch(
      User user, MandateBatch mandateBatch, PillarSuggestion pillarSuggestion, Locale locale) {

    // TODO

    EmailType emailType = EmailType.from(mandateBatch);
    String templateName = emailType.getTemplateName(locale);
    MandrillMessage mandrillMessage =
        emailService.newMandrillMessage(
            user.getEmail(),
            templateName,
            getMergeVars(user, pillarSuggestion),
            getMandateBatchTags(pillarSuggestion),
            getAttachments(user, mandateBatch));
    emailService
        .send(user, mandrillMessage, templateName)
        .ifPresent(
            response ->
                emailPersistenceService.save(
                    user, response.getId(), emailType, response.getStatus(), mandateBatch));
  }

  private Map<String, Object> getMergeVars(User user, PillarSuggestion pillarSuggestion) {
    var map = new HashMap<String, Object>();
    map.putAll(getNameMergeVars(user));
    map.putAll(getPillarSuggestionMergeVars(pillarSuggestion));
    return map;
  }

  private List<String> getMandateBatchTags(PillarSuggestion pillarSuggestion) {
    List<String> tags = new ArrayList<>();
    tags.add("mandate_batch");
    if (pillarSuggestion.isSuggestPaymentRate()) {
      tags.add("suggest_payment_rate");
    }
    if (pillarSuggestion.isSuggestThirdPillar()) {
      tags.add("suggest_3");
    }
    if (pillarSuggestion.isSuggestMembership()) {
      tags.add("suggest_member");
    }
    return tags;
  }
}
