package ee.tuleva.onboarding.capital.transfer;

import static ee.tuleva.onboarding.signature.SignatureFile.SignatureFileType.DIGIDOC_CONTAINER;
import static ee.tuleva.onboarding.signature.SignatureFile.SignatureFileType.HTML;
import static java.util.stream.Collectors.toList;

import ee.tuleva.onboarding.capital.transfer.content.CapitalTransferContentFile;
import ee.tuleva.onboarding.signature.SignatureFile;
import ee.tuleva.onboarding.signature.SignatureFile.SignatureFileType;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class CapitalTransferFileService {

  private final CapitalTransferContractRepository contractRepository;

  public List<SignatureFile> getContractFiles(Long contractId) {
    CapitalTransferContract contract =
        contractRepository
            .findById(contractId)
            .orElseThrow(
                () -> new IllegalArgumentException("Contract not found with id " + contractId));

    return getContractFiles(contract);
  }

  public List<SignatureFile> getContractFiles(CapitalTransferContract contract) {
    return getContentFiles(contract).stream()
        .map(file -> new SignatureFile(file.getName(), file.getFileType(), file.getContent()))
        .collect(toList());
  }

  private List<CapitalTransferContentFile> getContentFiles(CapitalTransferContract contract) {
    byte[] contentToSign = getContentToSign(contract);
    SignatureFileType fileType = getFileType(contract);
    String fileName = getFileName(contract);

    return List.of(
        CapitalTransferContentFile.builder()
            .name(fileName)
            .fileType(fileType)
            .content(contentToSign)
            .build());
  }

  private byte[] getContentToSign(CapitalTransferContract contract) {
    if (contract.getState() == CapitalTransferContractState.SELLER_SIGNED
        && contract.getDigiDocContainer() != null) {
      return contract.getDigiDocContainer();
    }
    return contract.getOriginalContent();
  }

  private SignatureFileType getFileType(CapitalTransferContract contract) {
    if (contract.getState() == CapitalTransferContractState.SELLER_SIGNED
        && contract.getDigiDocContainer() != null) {
      // https://www.id.ee/en/article/bdoc2-1-new-estonian-digital-signature-standard-format/
      return DIGIDOC_CONTAINER;
    }
    return HTML;
  }

  private String getFileName(CapitalTransferContract contract) {
    if (contract.getState() == CapitalTransferContractState.SELLER_SIGNED
        && contract.getDigiDocContainer() != null) {
      return "liikmekapital-%d.bdoc".formatted(contract.getId());
    }
    return "liikmekapital-%d.html".formatted(contract.getId());
  }
}
