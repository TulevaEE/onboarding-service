package ee.tuleva.onboarding.capital.transfer;

import static ee.tuleva.onboarding.auth.UserFixture.sampleUser;
import static ee.tuleva.onboarding.notification.email.EmailType.CAPITAL_TRANSFER_BUYER_TO_SIGN;
import static ee.tuleva.onboarding.notification.email.EmailType.CAPITAL_TRANSFER_CONFIRMED_BY_BUYER;
import static ee.tuleva.onboarding.notification.email.EmailType.CAPITAL_TRANSFER_CONFIRMED_BY_SELLER;
import static ee.tuleva.onboarding.user.MemberFixture.memberFixture;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.microtripit.mandrillapp.lutung.view.MandrillMessage;
import com.microtripit.mandrillapp.lutung.view.MandrillMessageStatus;
import ee.tuleva.onboarding.epis.ContactDetailsService;
import ee.tuleva.onboarding.notification.email.EmailPersistenceService;
import ee.tuleva.onboarding.notification.email.EmailService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CapitalTransferEmailSenderTest {

  @Mock private EmailService emailService;
  @Mock private EmailPersistenceService emailPersistenceService;
  @Mock private ContactDetailsService contactDetailsService;

  private CapitalTransferEmailSender emailSender;

  @BeforeEach
  void setUp() {
    emailSender =
        new CapitalTransferEmailSender(
            emailService, emailPersistenceService, contactDetailsService);
  }

  @Test
  void sendContractEmailUsesBuyerToSignTemplateWithoutAttachment() {
    var recipient = sampleUser().build();
    var contract =
        CapitalTransferContract.builder()
            .id(1L)
            .seller(memberFixture().id(2L).user(sampleUser().build()).build())
            .buyer(memberFixture().id(3L).user(sampleUser().build()).build())
            .digiDocContainer(new byte[0])
            .build();

    when(emailService.newMandrillMessage(
            eq(recipient.getEmail()),
            eq("capital_transfer_buyer_to_sign_et"),
            any(),
            eq(List.of("capital-transfer")),
            eq(List.<MandrillMessage.MessageContent>of())))
        .thenReturn(new MandrillMessage());
    when(emailService.send(eq(recipient), any(), eq("capital_transfer_buyer_to_sign_et")))
        .thenReturn(Optional.of(new MandrillMessageStatus()));

    emailSender.sendContractEmail(recipient, CAPITAL_TRANSFER_BUYER_TO_SIGN, contract);

    verify(emailService)
        .newMandrillMessage(
            eq(recipient.getEmail()),
            eq("capital_transfer_buyer_to_sign_et"),
            any(),
            eq(List.of("capital-transfer")),
            eq(List.<MandrillMessage.MessageContent>of()));
    verify(emailPersistenceService)
        .save(eq(recipient), any(), eq(CAPITAL_TRANSFER_BUYER_TO_SIGN), any());
  }

  @Test
  void sendContractEmailUsesConfirmedByBuyerTemplateWithAttachment() {
    var recipient = sampleUser().build();
    var contract =
        CapitalTransferContract.builder()
            .id(1L)
            .seller(memberFixture().id(2L).user(sampleUser().build()).build())
            .buyer(memberFixture().id(3L).user(sampleUser().build()).build())
            .digiDocContainer(new byte[0])
            .build();

    when(emailService.newMandrillMessage(
            eq(recipient.getEmail()),
            eq("capital_transfer_confirmed_by_buyer_et"),
            any(),
            eq(List.of("capital-transfer")),
            argThat(attachments -> attachments.size() == 1)))
        .thenReturn(new MandrillMessage());
    when(emailService.send(eq(recipient), any(), eq("capital_transfer_confirmed_by_buyer_et")))
        .thenReturn(Optional.of(new MandrillMessageStatus()));

    emailSender.sendContractEmail(recipient, CAPITAL_TRANSFER_CONFIRMED_BY_BUYER, contract);

    verify(emailService)
        .newMandrillMessage(
            eq(recipient.getEmail()),
            eq("capital_transfer_confirmed_by_buyer_et"),
            any(),
            eq(List.of("capital-transfer")),
            argThat(attachments -> attachments.size() == 1));
    verify(emailPersistenceService)
        .save(eq(recipient), any(), eq(CAPITAL_TRANSFER_CONFIRMED_BY_BUYER), any());
  }

  @Test
  void sendContractEmailUsesConfirmedBySellerTemplateWithAttachment() {
    var recipient = sampleUser().build();
    var contract =
        CapitalTransferContract.builder()
            .id(1L)
            .seller(memberFixture().id(2L).user(sampleUser().build()).build())
            .buyer(memberFixture().id(3L).user(sampleUser().build()).build())
            .digiDocContainer(new byte[0])
            .build();

    when(emailService.newMandrillMessage(
            eq(recipient.getEmail()),
            eq("capital_transfer_confirmed_by_seller_et"),
            any(),
            eq(List.of("capital-transfer")),
            argThat(attachments -> attachments.size() == 1)))
        .thenReturn(new MandrillMessage());
    when(emailService.send(eq(recipient), any(), eq("capital_transfer_confirmed_by_seller_et")))
        .thenReturn(Optional.of(new MandrillMessageStatus()));

    emailSender.sendContractEmail(recipient, CAPITAL_TRANSFER_CONFIRMED_BY_SELLER, contract);

    verify(emailService)
        .newMandrillMessage(
            eq(recipient.getEmail()),
            eq("capital_transfer_confirmed_by_seller_et"),
            any(),
            eq(List.of("capital-transfer")),
            argThat(attachments -> attachments.size() == 1));
    verify(emailPersistenceService)
        .save(eq(recipient), any(), eq(CAPITAL_TRANSFER_CONFIRMED_BY_SELLER), any());
  }
}
