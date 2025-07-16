package ee.tuleva.onboarding.capital.transfer.content;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CapitalTransferContentFile {
  private final String mimeType = "text/html";
  private String name;
  private byte[] content;
}
