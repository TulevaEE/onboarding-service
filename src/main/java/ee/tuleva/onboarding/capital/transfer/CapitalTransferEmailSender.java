package ee.tuleva.onboarding.capital.transfer;

import static ee.tuleva.onboarding.notification.email.EmailType.*;

import com.microtripit.mandrillapp.lutung.view.MandrillMessage;
import com.microtripit.mandrillapp.lutung.view.MandrillMessageStatus;
import ee.tuleva.onboarding.epis.ContactDetailsService;
import ee.tuleva.onboarding.notification.email.EmailPersistenceService;
import ee.tuleva.onboarding.notification.email.EmailService;
import ee.tuleva.onboarding.notification.email.EmailType;
import ee.tuleva.onboarding.user.User;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class CapitalTransferEmailSender {

  private final EmailService emailService;
  private final EmailPersistenceService emailPersistenceService;
  private final ContactDetailsService contactDetailsService;

  public void sendContractEmail(
      User recipient, EmailType emailType, CapitalTransferContract contract) {
    if (recipient.getEmail() == null) {
      log.error("User {} has no email, not sending email {}", recipient.getId(), emailType);
      return;
    }

    Map<String, Object> mergeVars =
        Map.of(
            "fname", recipient.getFirstName(),
            "lname", recipient.getLastName(),
            "sellerFirstName", contract.getSellerFirstName(),
            "sellerLastName", contract.getSellerLastName(),
            "sellerFullName", contract.getSellerFullName(),
            "buyerFirstName", contract.getBuyerFirstName(),
            "buyerLastName", contract.getBuyerLastName(),
            "buyerFullName", contract.getBuyerFullName(),
            "contractId", contract.getId());

    var templateName = emailType.getTemplateName(getLanguage(recipient));
    var attachments =
        Set.of(CAPITAL_TRANSFER_CONFIRMED_BY_BUYER, CAPITAL_TRANSFER_CONFIRMED_BY_SELLER)
                .contains(emailType)
            ? contractAttachments(contract)
            : List.<MandrillMessage.MessageContent>of();

    MandrillMessage message =
        emailService.newMandrillMessage(
            recipient.getEmail(),
            templateName,
            mergeVars,
            List.of("capital-transfer"),
            attachments);

    Optional<MandrillMessageStatus> response = emailService.send(recipient, message, templateName);
    if (response.isPresent()) {
      var responseValue = response.get();
      emailPersistenceService.save(
          recipient, responseValue.getId(), emailType, responseValue.getStatus());
    }
  }

  private String getLanguage(User user) {
    //    var contactDetails = contactDetailsService.getContactDetails(user);
    //    return contactDetails.getLanguagePreference() == ENG ? "en" : "et";

    // hotfix: there is no authentication context when sending the email after board approval
    return "et";
  }

  private static List<MandrillMessage.MessageContent> contractAttachments(
      CapitalTransferContract contract) {
    MandrillMessage.MessageContent attachment = new MandrillMessage.MessageContent();
    attachment.setName("liikmekapitali_avaldus" + contract.getId() + ".bdoc");
    attachment.setType("application/bdoc");
    attachment.setContent(Base64.getEncoder().encodeToString(contract.getDigiDocContainer()));
    return List.of(attachment);
  }
}
