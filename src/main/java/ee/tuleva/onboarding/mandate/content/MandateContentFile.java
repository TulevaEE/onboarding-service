package ee.tuleva.onboarding.mandate.content;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class MandateContentFile {
  private final String mimeType = "text/html";
  private String name;
  private byte[] content;
}
