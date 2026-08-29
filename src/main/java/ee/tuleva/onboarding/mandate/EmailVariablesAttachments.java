package ee.tuleva.onboarding.mandate;

import static java.util.Collections.singletonList;
import static java.util.Objects.requireNonNull;

import com.microtripit.mandrillapp.lutung.view.MandrillMessage;
import ee.tuleva.onboarding.mandate.batch.MandateBatch;
import ee.tuleva.onboarding.user.Names;
import ee.tuleva.onboarding.user.User;
import java.util.Base64;
import java.util.List;
import java.util.Map;

public class EmailVariablesAttachments {

  public static Map<String, Object> getPillarSuggestionMergeVars(
      PillarSuggestion pillarSuggestion, String savingsFundFee) {
    return Map.ofEntries(
        Map.entry("savingsFundFee", savingsFundFee),
        Map.entry("suggestPaymentRate", pillarSuggestion.isSuggestPaymentRate()),
        Map.entry("suggestMembership", pillarSuggestion.isSuggestMembership()),
        Map.entry("suggestSecondPillar", pillarSuggestion.isSuggestSecondPillar()),
        Map.entry("suggestThirdPillar", pillarSuggestion.isSuggestThirdPillar()),
        Map.entry("thirdPillarActive", pillarSuggestion.isThirdPillarActive()),
        Map.entry("leftSecondPillar", pillarSuggestion.isLeftSecondPillar()),
        Map.entry("suggestSavingsFund", pillarSuggestion.isSuggestSavingsFund()),
        Map.entry(
            "suggestThirdPillarRecurringPayment",
            pillarSuggestion.isSuggestThirdPillarRecurringPayment()),
        Map.entry("suggestThirdPillarRaise", pillarSuggestion.isSuggestThirdPillarRaise()),
        Map.entry(
            "suggestSavingsFundRecurringPayment",
            pillarSuggestion.isSuggestSavingsFundRecurringPayment()));
  }

  public static Map<String, Object> getNameMergeVars(User user) {
    return Map.of(
        "fname",
        Names.formatted(user.getFirstName()),
        "lname",
        Names.formatted(user.getLastName()));
  }

  public static List<MandrillMessage.MessageContent> getAttachments(User user, Mandate mandate) {
    return singletonList(
        getAttachment(
            getNameSuffix(user) + "_avaldus_" + mandate.getId() + ".bdoc",
            mandate.getSignedFile()));
  }

  public static List<MandrillMessage.MessageContent> getAttachments(
      User user, MandateBatch mandateBatch) {
    return singletonList(
        getAttachment(
            getNameSuffix(user) + "_avaldused_" + mandateBatch.getId() + ".bdoc",
            requireNonNull(
                mandateBatch.getFile(),
                "Mandate batch file missing: mandateBatchId=" + mandateBatch.getId())));
  }

  private static MandrillMessage.MessageContent getAttachment(String fileName, byte[] file) {
    MandrillMessage.MessageContent attachment = new MandrillMessage.MessageContent();

    attachment.setName(fileName);
    attachment.setType("application/bdoc");
    attachment.setContent(Base64.getEncoder().encodeToString(file));

    return attachment;
  }

  private static String getNameSuffix(User user) {
    return (user.getFirstName() + "_" + user.getLastName())
        .toLowerCase()
        .replace('õ', 'o')
        .replace('ä', 'a')
        .replace('ö', 'o')
        .replace('ü', 'u')
        .replace('š', 's')
        .replace('ž', 'z')
        .replaceAll("[^a-z0-9_.-]", "_");
  }
}
