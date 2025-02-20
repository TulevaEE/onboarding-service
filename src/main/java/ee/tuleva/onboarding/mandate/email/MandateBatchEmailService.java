package ee.tuleva.onboarding.mandate.email;

import static ee.tuleva.onboarding.mandate.MandateType.FUND_PENSION_OPENING;
import static ee.tuleva.onboarding.mandate.MandateType.PARTIAL_WITHDRAWAL;
import static ee.tuleva.onboarding.mandate.email.EmailVariablesAttachments.*;
import static ee.tuleva.onboarding.mandate.email.persistence.EmailType.BATCH_FAILED;
import static ee.tuleva.onboarding.pillar.Pillar.SECOND;
import static ee.tuleva.onboarding.pillar.Pillar.THIRD;
import static java.util.stream.Stream.concat;

import com.microtripit.mandrillapp.lutung.view.MandrillMessage;
import ee.tuleva.onboarding.epis.mandate.details.FundPensionOpeningMandateDetails;
import ee.tuleva.onboarding.epis.mandate.details.PartialWithdrawalMandateDetails;
import ee.tuleva.onboarding.mandate.batch.MandateBatch;
import ee.tuleva.onboarding.mandate.email.persistence.EmailPersistenceService;
import ee.tuleva.onboarding.mandate.email.persistence.EmailType;
import ee.tuleva.onboarding.mandate.processor.MandateProcessorService;
import ee.tuleva.onboarding.notification.email.EmailService;
import ee.tuleva.onboarding.pillar.Pillar;
import ee.tuleva.onboarding.user.User;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class MandateBatchEmailService {

  private final EmailService emailService;
  private final EmailPersistenceService emailPersistenceService;
  private final MandateProcessorService mandateProcessor;

  public void sendMandateBatch(
      User user, MandateBatch mandateBatch, PillarSuggestion pillarSuggestion, Locale locale) {

    if (emailPersistenceService.hasEmailsFor(mandateBatch)) {
      log.warn(
          "Skipping mandatebatch (id={}) email as email already present", mandateBatch.getId());
      return;
    }

    EmailType emailType = EmailType.from(mandateBatch);
    String templateName = emailType.getTemplateName(locale);
    MandrillMessage mandrillMessage =
        emailService.newMandrillMessage(
            user.getEmail(),
            templateName,
            getMergeVars(user, mandateBatch, pillarSuggestion),
            getMandateBatchTags(mandateBatch),
            getAttachments(user, mandateBatch));
    emailService
        .send(user, mandrillMessage, templateName)
        .ifPresent(
            response ->
                emailPersistenceService.save(
                    user, response.getId(), emailType, response.getStatus(), mandateBatch));
  }

  public void sendMandateBatchFailedEmail(User user, MandateBatch mandateBatch, Locale locale) {
    var emailType = BATCH_FAILED;
    String templateName = emailType.getTemplateName(locale);

    Map<String, Object> mergeVars = getMergeVars(user, mandateBatch);
    mergeVars.putAll(getFailedBatchMergeVars(mandateBatch));

    MandrillMessage mandrillMessage =
        emailService.newMandrillMessage(
            user.getEmail(),
            templateName,
            mergeVars,
            getMandateBatchTags(mandateBatch),
            getAttachments(user, mandateBatch));
    emailService
        .send(user, mandrillMessage, templateName)
        .ifPresent(
            response ->
                emailPersistenceService.save(
                    user, response.getId(), emailType, response.getStatus(), mandateBatch));
  }

  private Map<String, Object> getMergeVars(
      User user, MandateBatch batch, PillarSuggestion pillarSuggestion) {
    var map = new HashMap<String, Object>();
    map.putAll(getNameMergeVars(user));
    map.putAll(getPillarSuggestionMergeVars(pillarSuggestion));
    map.putAll(getWithdrawalMandateMergeVars(batch));

    return map;
  }

  private Map<String, Object> getMergeVars(User user, MandateBatch batch) {
    var map = new HashMap<String, Object>();
    map.putAll(getNameMergeVars(user));
    map.putAll(getWithdrawalMandateMergeVars(batch));

    return map;
  }

  private Map<String, Object> getFailedBatchMergeVars(MandateBatch mandateBatch) {
    var map = new HashMap<String, Object>();

    var mandates = mandateBatch.getMandates();

    int failedMandateCount =
        mandates.stream()
            .filter(mandate -> !mandateProcessor.getErrors(mandate).getErrors().isEmpty())
            .toList()
            .size();

    int succesfulMandateCount = mandates.size() - failedMandateCount;

    map.put("failedMandateCount", failedMandateCount);
    map.put("succesfulMandateCount", succesfulMandateCount);
    map.put("totalMandateCount", mandates.size());

    return map;
  }

  private Map<String, Object> getWithdrawalMandateMergeVars(MandateBatch batch) {
    var map = new HashMap<String, Object>();

    var fundPensionMandatePillars = getFundPensionMandatePillars(batch);
    var partialWithdrawalMandatePillars = getPartialWithdrawalMandatesPillars(batch);

    return Map.of(
        "fundPensionSecondPillar", fundPensionMandatePillars.contains(SECOND),
        "fundPensionThirdPillar", fundPensionMandatePillars.contains(THIRD),
        "partialWithdrawalSecondPillar", partialWithdrawalMandatePillars.contains(SECOND),
        "partialWithdrawalThirdPillar", partialWithdrawalMandatePillars.contains(THIRD));
  }

  private Set<Pillar> getFundPensionMandatePillars(MandateBatch batch) {
    return batch.getMandates().stream()
        .filter(mandate -> mandate.getMandateType() == FUND_PENSION_OPENING)
        .map(
            mandate ->
                ((FundPensionOpeningMandateDetails) mandate.getMandateDto().getDetails())
                    .getPillar())
        .collect(Collectors.toSet());
  }

  private Set<Pillar> getPartialWithdrawalMandatesPillars(MandateBatch batch) {
    return batch.getMandates().stream()
        .filter(mandate -> mandate.getMandateType() == PARTIAL_WITHDRAWAL)
        .map(
            mandate ->
                ((PartialWithdrawalMandateDetails) mandate.getMandateDto().getDetails())
                    .getPillar())
        .collect(Collectors.toSet());
  }

  private List<String> getMandateBatchTags(MandateBatch batch) {
    List<String> tags = new ArrayList<>();
    tags.add("mandate_batch");

    var fundPensionPillars = getFundPensionMandatePillars(batch);
    var partialWithdrawalPillars = getPartialWithdrawalMandatesPillars(batch);

    var allPillars =
        concat(fundPensionPillars.stream(), partialWithdrawalPillars.stream())
            .collect(Collectors.toSet());

    if (allPillars.contains(SECOND)) {
      tags.add("pillar_2");
    }

    if (allPillars.contains(THIRD)) {
      tags.add("pillar_3");
    }

    if (!fundPensionPillars.isEmpty()) {
      tags.add("fund_pension_opening");
    }

    if (!partialWithdrawalPillars.isEmpty()) {
      tags.add("partial_withdrawal");
    }

    return tags;
  }
}
