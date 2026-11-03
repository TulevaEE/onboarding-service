package ee.tuleva.onboarding.mandate;

import static java.util.Collections.singletonList;
import static java.util.Objects.requireNonNull;

import com.microtripit.mandrillapp.lutung.view.MandrillMessage;
import ee.tuleva.onboarding.auth.principal.Names;
import ee.tuleva.onboarding.mandate.batch.MandateBatch;
import ee.tuleva.onboarding.notification.email.SignedContainerAttachment;
import ee.tuleva.onboarding.user.User;
import java.util.List;
import java.util.Map;

public class EmailVariablesAttachments {

  public static Map<String, Object> getNameMergeVars(User user) {
    return Map.of(
        "fname",
        Names.formatted(user.getFirstName()),
        "lname",
        Names.formatted(user.getLastName()));
  }

  public static List<MandrillMessage.MessageContent> getAttachments(User user, Mandate mandate) {
    return singletonList(
        SignedContainerAttachment.of(
            getNameSuffix(user) + "_avaldus_" + mandate.getId(), mandate.getSignedFile()));
  }

  public static List<MandrillMessage.MessageContent> getAttachments(
      User user, MandateBatch mandateBatch) {
    return singletonList(
        SignedContainerAttachment.of(
            getNameSuffix(user) + "_avaldused_" + mandateBatch.getId(),
            requireNonNull(
                mandateBatch.getFile(),
                "Mandate batch file missing: mandateBatchId=" + mandateBatch.getId())));
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
