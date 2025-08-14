package ee.tuleva.onboarding.capital.transfer.content;

import static ee.tuleva.onboarding.signature.SignatureFile.SignatureFileType.HTML;

import ee.tuleva.onboarding.signature.SignatureFile.SignatureFileType;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CapitalTransferContentFile {
  @Builder.Default private SignatureFileType fileType = HTML;
  private String name;
  private byte[] content;
}
