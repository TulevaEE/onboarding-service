package ee.tuleva.onboarding.mandate.email;

import static java.util.Collections.singletonList;

import com.microtripit.mandrillapp.lutung.view.MandrillMessage;
import ee.tuleva.onboarding.mandate.Mandate;
import ee.tuleva.onboarding.mandate.batch.MandateBatch;
import ee.tuleva.onboarding.user.User;
import java.util.*;

public class EmailVariablesAttachments {
  public static Map<String, Object> getPillarSuggestionMergeVars(
      PillarSuggestion pillarSuggestion) {
    return Map.of(
        "suggestPaymentRate", pillarSuggestion.isSuggestPaymentRate(),
        "suggestMembership", pillarSuggestion.isSuggestMembership(),
        "suggestSecondPillar", pillarSuggestion.isSuggestSecondPillar(),
        "suggestThirdPillar", pillarSuggestion.isSuggestThirdPillar());
  }

  public static List<String> getPillarSuggestionTags(PillarSuggestion pillarSuggestion) {
    List<String> tags = new ArrayList<>();

    if (pillarSuggestion.isSuggestPaymentRate()) {
      tags.add("suggest_payment_rate");
    }
    if (pillarSuggestion.isSuggestSecondPillar()) {
      tags.add("suggest_2");
    }
    if (pillarSuggestion.isSuggestThirdPillar()) {
      tags.add("suggest_3");
    }
    if (pillarSuggestion.isSuggestMembership()) {
      tags.add("suggest_member");
    }

    return tags;
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

  private static MandrillMessage.MessageContent getAttachment(String fileName, byte[] file) {
    MandrillMessage.MessageContent attachment = new MandrillMessage.MessageContent();

    attachment.setName(fileName);
    attachment.setType("application/bdoc");
    attachment.setContent(Base64.getEncoder().encodeToString(file));

    return attachment;
  }

  private static String getNameSuffix(User user) {
    String nameSuffix = user.getFirstName() + "_" + user.getLastName();
    nameSuffix = nameSuffix.toLowerCase();
    nameSuffix.replace("õ", "o");
    nameSuffix.replace("ä", "a");
    nameSuffix.replace("ö", "o");
    nameSuffix.replace("ü", "u");
    nameSuffix.replaceAll("[^a-zA-Z0-9_\\-\\.]", "_");
    return nameSuffix;
  }
}
