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
    return List.of(
        CapitalTransferContentFile.builder()
            .name("liikmekapital-%d.html".formatted(contract.getId()))
            .content(contract.getOriginalContent())
            .build());
  }
}
