package ee.tuleva.onboarding.mandate.email;

import static java.util.Collections.singletonList;

import com.microtripit.mandrillapp.lutung.view.MandrillMessage;
import ee.tuleva.onboarding.capital.transfer.CapitalTransferContract;
import ee.tuleva.onboarding.mandate.Mandate;
import ee.tuleva.onboarding.mandate.batch.MandateBatch;
import ee.tuleva.onboarding.user.User;
import java.util.Base64;
import java.util.List;
import java.util.Map;

public class EmailVariablesAttachments {
  public static Map<String, Object> getPillarSuggestionMergeVars(
      PillarSuggestion pillarSuggestion) {
    return Map.of(
        "suggestPaymentRate", pillarSuggestion.isSuggestPaymentRate(),
        "suggestMembership", pillarSuggestion.isSuggestMembership(),
        "suggestSecondPillar", pillarSuggestion.isSuggestSecondPillar(),
        "suggestThirdPillar", pillarSuggestion.isSuggestThirdPillar());
  }

  public static Map<String, Object> getNameMergeVars(User user) {
    return Map.of("fname", user.getFirstName(), "lname", user.getLastName());
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
            mandateBatch.getFile()));
  }

  public static List<MandrillMessage.MessageContent> getAttachments(
      CapitalTransferContract capitalTransferContract) {
    return singletonList(
        getAttachment(
            "liikmekapitali_avaldus" + capitalTransferContract.getId() + ".bdoc",
            capitalTransferContract.getDigiDocContainer()));
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
