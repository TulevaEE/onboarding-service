package ee.tuleva.onboarding.capital.transfer;

import static java.util.stream.Collectors.toList;

import ee.tuleva.onboarding.capital.transfer.content.CapitalTransferContentFile;
import ee.tuleva.onboarding.mandate.signature.SignatureFile;
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
        .map(file -> new SignatureFile(file.getName(), file.getMimeType(), file.getContent()))
        .collect(toList());
  }

  private List<CapitalTransferContentFile> getContentFiles(CapitalTransferContract contract) {
    byte[] contentToSign = getContentToSign(contract);
    String mimeType = getMimeType(contract);
    String fileName = getFileName(contract);

    return List.of(
        CapitalTransferContentFile.builder()
            .name(fileName)
            .mimeType(mimeType)
            .content(contentToSign)
            .build());
  }

  private byte[] getContentToSign(CapitalTransferContract contract) {
    if (contract.getState() == CapitalTransferContractState.SELLER_SIGNED
        && contract.getDigiDocContainer() != null) {
      // TODO: Lift this logic up and use it at signing point – do we need to create new container
      // oad signature to it?
      // TODO: or don't lift and use magic bytes at signer level... digidoc container magic bytes
      // appear to be zip ones 50 4b
      return contract.getDigiDocContainer();
    }
    return contract.getOriginalContent();
  }

  private String getMimeType(CapitalTransferContract contract) {
    if (contract.getState() == CapitalTransferContractState.SELLER_SIGNED
        && contract.getDigiDocContainer() != null) {
      // https://www.id.ee/en/article/bdoc2-1-new-estonian-digital-signature-standard-format/
      return "application/vnd.etsi.asic-e+zip";
    }
    return "text/html";
  }

  private String getFileName(CapitalTransferContract contract) {
    if (contract.getState() == CapitalTransferContractState.SELLER_SIGNED
        && contract.getDigiDocContainer() != null) {
      return "liikmekapital-%d.bdoc".formatted(contract.getId());
    }
    return "liikmekapital-%d.html".formatted(contract.getId());
  }
}
