package ee.tuleva.onboarding.notification.email;

import static ee.tuleva.onboarding.notification.email.EmailType.CONTACT_DETAILS_CHANGED;
import static ee.tuleva.onboarding.notification.email.EmailType.PREVIOUS_CONTACT_ACTIVITY;
import static ee.tuleva.onboarding.user.ContactField.EMAIL;
import static ee.tuleva.onboarding.user.ContactField.PHONE;
import static java.util.function.Predicate.not;
import static java.util.stream.Collectors.toSet;

import ee.tuleva.onboarding.auth.principal.Names;
import ee.tuleva.onboarding.user.ContactChange;
import ee.tuleva.onboarding.user.ContactField;
import ee.tuleva.onboarding.user.User;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

record PreviousContactNotice(EmailType type, List<Recipient> recipients) {

  record Recipient(String email, Map<String, Object> mergeVars) {

    Recipient {
      mergeVars = Map.copyOf(mergeVars);
    }
  }

  PreviousContactNotice {
    recipients = List.copyOf(recipients);
  }

  static PreviousContactNotice ofContactChange(
      User user, List<ContactChange> changes, List<String> replacedEmails) {
    Set<ContactField> changedFields =
        changes.stream()
            .filter(not(ContactChange::onlyChangesLetterCase))
            .map(ContactChange::field)
            .collect(toSet());
    if (changedFields.isEmpty()) {
      return new PreviousContactNotice(CONTACT_DETAILS_CHANGED, List.of());
    }
    var whatChanged = new HashMap<>(firstNameOnly(user));
    whatChanged.put("emailChanged", changedFields.contains(EMAIL));
    whatChanged.put("phoneChanged", changedFields.contains(PHONE));
    Stream<Recipient> previousAddresses =
        replacedEmails.stream()
            .map(email -> new Recipient(email, with(whatChanged, "previousAddress", true)));
    return new PreviousContactNotice(
        CONTACT_DETAILS_CHANGED,
        Stream.concat(previousAddresses, currentAddress(user, changedFields, whatChanged))
            .toList());
  }

  static PreviousContactNotice ofActivity(User user, List<String> replacedEmails) {
    var mergeVars = firstNameOnly(user);
    return new PreviousContactNotice(
        PREVIOUS_CONTACT_ACTIVITY,
        replacedEmails.stream().map(email -> new Recipient(email, mergeVars)).toList());
  }

  private static Stream<Recipient> currentAddress(
      User user, Set<ContactField> changedFields, Map<String, Object> whatChanged) {
    String currentEmail = user.getEmail();
    if (currentEmail == null || !changedFields.equals(Set.of(PHONE))) {
      return Stream.empty();
    }
    return Stream.of(new Recipient(currentEmail, with(whatChanged, "previousAddress", false)));
  }

  private static Map<String, Object> firstNameOnly(User user) {
    return Map.of("fname", Names.formatted(user.getFirstName()));
  }

  private static Map<String, Object> with(Map<String, Object> mergeVars, String key, Object value) {
    var extended = new HashMap<>(mergeVars);
    extended.put(key, value);
    return extended;
  }
}
