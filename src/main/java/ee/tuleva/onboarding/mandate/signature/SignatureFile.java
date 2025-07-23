package ee.tuleva.onboarding.mandate.signature;

import static java.util.Arrays.copyOfRange;

import java.io.Serial;
import java.io.Serializable;
import java.util.Arrays;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public class SignatureFile implements Serializable {

  private static final byte[] EXISTING_SIGNATURE_CONTAINER_MAGIC_BYTES = {
    0x50, 0x4b
  }; // ZIP archive/signature container magic bytes

  @Serial private static final long serialVersionUID = -2405222829412049325L;

  private final String name;
  private final String mimeType;
  private final byte[] content;

  public boolean isContainer() {
    // TODO use mimetype?
    var fileMagicBytes = copyOfRange(content, 0, 2);

    // is zip archive?
    return Arrays.equals(fileMagicBytes, EXISTING_SIGNATURE_CONTAINER_MAGIC_BYTES);
  }
}
