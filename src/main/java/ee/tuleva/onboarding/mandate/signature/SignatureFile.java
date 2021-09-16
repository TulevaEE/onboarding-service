package ee.tuleva.onboarding.mandate.signature;

import java.io.Serial;
import java.io.Serializable;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public class SignatureFile implements Serializable {

  @Serial private static final long serialVersionUID = -2405222829412049325L;

  private final String name;
  private final String mimeType;
  private final byte[] content;
}
