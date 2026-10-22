package ee.tuleva.onboarding.signature;

import static ee.tuleva.onboarding.signature.SignatureFile.SignatureFileType.DIGIDOC_CONTAINER;

import java.io.Serial;
import java.io.Serializable;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public class SignatureFile implements Serializable {
  public SignatureFile(String name, SignatureFileType fileType, byte[] content) {
    this.name = name;
    this.mimeType = fileType.getMimeType();
    this.content = content;
  }

  public enum SignatureFileType {
    HTML("text/html", "html"),
    PDF("application/pdf", "pdf"),
    DIGIDOC_CONTAINER("application/vnd.etsi.asic-e+zip", "asice");

    @Getter private final String mimeType;
    private final String extension;

    SignatureFileType(String mimeType, String extension) {
      this.mimeType = mimeType;
      this.extension = extension;
    }

    public String fileName(String baseName) {
      return baseName + "." + extension;
    }
  }

  @Serial private static final long serialVersionUID = -2405222829412049325L;

  private final String name;
  private final String mimeType;
  private final byte[] content;

  public boolean isContainer() {
    return this.mimeType.equals(DIGIDOC_CONTAINER.getMimeType());
  }
}
