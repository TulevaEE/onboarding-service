package ee.tuleva.onboarding.capital.transfer;

import static ee.tuleva.onboarding.capital.transfer.CapitalTransferContractFixture.sampleCapitalTransferContractWithSeller;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import ee.tuleva.onboarding.signature.SignatureFile;
import ee.tuleva.onboarding.user.MemberFixture;
import ee.tuleva.onboarding.user.member.Member;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CapitalTransferFileServiceTest {

  @Mock private CapitalTransferContractRepository contractRepository;

  @InjectMocks private CapitalTransferFileService fileService;

  @Test
  void getContractFiles_returnsOriginalContentForCreatedContract() {
    // given
    Long contractId = 1L;
    byte[] originalContent = "original content".getBytes();
    Member seller = MemberFixture.memberFixture().build();
    CapitalTransferContract contract =
        sampleCapitalTransferContractWithSeller(seller)
            .id(contractId)
            .state(CapitalTransferContractState.CREATED)
            .originalContent(originalContent)
            .digiDocContainer(null)
            .build();

    when(contractRepository.findById(contractId)).thenReturn(Optional.of(contract));

    // when
    List<SignatureFile> files = fileService.getContractFiles(contractId);

    // then
    assertThat(files).hasSize(1);
    SignatureFile file = files.get(0);
    assertThat(file.getName()).isEqualTo("liikmekapital-1.html");
    assertThat(file.getMimeType()).isEqualTo("text/html");
    assertThat(file.getContent()).isEqualTo(originalContent);
  }

  @Test
  void getContractFiles_returnsSignedContainerForSellerSignedContract() {
    // given
    Long contractId = 1L;
    byte[] originalContent = "original content".getBytes();
    byte[] signedContainer = "signed container content".getBytes();
    Member seller = MemberFixture.memberFixture().build();
    CapitalTransferContract contract =
        sampleCapitalTransferContractWithSeller(seller)
            .id(contractId)
            .state(CapitalTransferContractState.SELLER_SIGNED)
            .originalContent(originalContent)
            .digiDocContainer(signedContainer)
            .build();

    when(contractRepository.findById(contractId)).thenReturn(Optional.of(contract));

    // when
    List<SignatureFile> files = fileService.getContractFiles(contractId);

    // then
    assertThat(files).hasSize(1);
    SignatureFile file = files.get(0);
    assertThat(file.getName()).isEqualTo("liikmekapital-1.bdoc");
    assertThat(file.getMimeType()).isEqualTo("application/vnd.etsi.asic-e+zip");
    assertThat(file.getContent()).isEqualTo(signedContainer);
  }

  @Test
  void getContractFiles_returnsOriginalContentWhenSellerSignedButNoContainer() {
    // given
    Long contractId = 1L;
    byte[] originalContent = "original content".getBytes();
    Member seller = MemberFixture.memberFixture().build();
    CapitalTransferContract contract =
        sampleCapitalTransferContractWithSeller(seller)
            .id(contractId)
            .state(CapitalTransferContractState.SELLER_SIGNED)
            .originalContent(originalContent)
            .digiDocContainer(null) // No container despite being signed
            .build();

    when(contractRepository.findById(contractId)).thenReturn(Optional.of(contract));

    // when
    List<SignatureFile> files = fileService.getContractFiles(contractId);

    // then
    assertThat(files).hasSize(1);
    SignatureFile file = files.get(0);
    assertThat(file.getName()).isEqualTo("liikmekapital-1.html");
    assertThat(file.getMimeType()).isEqualTo("text/html");
    assertThat(file.getContent()).isEqualTo(originalContent);
  }
}
