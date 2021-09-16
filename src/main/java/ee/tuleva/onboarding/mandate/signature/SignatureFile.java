package ee.tuleva.onboarding.mandate.signature;

import java.io.Serializable;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public class SignatureFile implements Serializable {

  private final String name;
  private final String mimeType;
  private final byte[] content;
}
