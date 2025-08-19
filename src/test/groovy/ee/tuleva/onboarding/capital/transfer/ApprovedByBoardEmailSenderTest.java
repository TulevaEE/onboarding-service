package ee.tuleva.onboarding.capital.transfer;

import static ee.tuleva.onboarding.auth.UserFixture.sampleUser;
import static ee.tuleva.onboarding.capital.transfer.CapitalTransferContractState.APPROVED;
import static ee.tuleva.onboarding.capital.transfer.CapitalTransferContractState.APPROVED_AND_NOTIFIED;
import static ee.tuleva.onboarding.mandate.email.persistence.EmailType.CAPITAL_TRANSFER_APPROVED_BY_BOARD;
import static ee.tuleva.onboarding.user.MemberFixture.memberFixture;
import static org.mockito.Mockito.*;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ApprovedByBoardEmailSenderTest {
  @Mock private CapitalTransferContractRepository capitalTransferContractRepository;
  @Mock private CapitalTransferContractService capitalTransferContractService;
  @InjectMocks private ApprovedByBoardEmailSender approvedByBoardEmailSender;

  @Test
  @DisplayName("sends emails for approved capital transfers and sets status")
  void sendEmailsForApprovedCapitalTransfers() {

    var buyerMember =
        memberFixture().id(2L).user(sampleUser().email("buyer@ostja.ee").build()).build();
    var sellerMember =
        memberFixture().id(3L).user(sampleUser().email("seller@sarikaline.ee").build()).build();
    var contract =
        CapitalTransferContract.builder()
            .id(1L)
            .state(APPROVED)
            .seller(buyerMember)
            .buyer(sellerMember)
            .build();

    when(capitalTransferContractRepository.findAllByState(APPROVED)).thenReturn(List.of(contract));

    approvedByBoardEmailSender.sendBoardApprovedEmails();

    verify(capitalTransferContractService, times(1))
        .sendContractEmail(
            eq(buyerMember.getUser()), eq(CAPITAL_TRANSFER_APPROVED_BY_BOARD), eq(contract));
    verify(capitalTransferContractService, times(1))
        .sendContractEmail(
            eq(sellerMember.getUser()), eq(CAPITAL_TRANSFER_APPROVED_BY_BOARD), eq(contract));
    verify(capitalTransferContractService, times(1))
        .updateStateBySystem(contract.getId(), APPROVED_AND_NOTIFIED);
  }

  @Test
  @DisplayName("does not send if none found")
  void sendEmailsForApprovedCapitalTransfersNoneFound() {

    when(capitalTransferContractRepository.findAllByState(APPROVED)).thenReturn(List.of());

    approvedByBoardEmailSender.sendBoardApprovedEmails();

    verify(capitalTransferContractService, never()).sendContractEmail(any(), any(), any());
    verify(capitalTransferContractService, never())
        .updateStateBySystem(any(), eq(APPROVED_AND_NOTIFIED));
  }
}
