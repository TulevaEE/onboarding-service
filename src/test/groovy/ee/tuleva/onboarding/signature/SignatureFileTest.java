package ee.tuleva.onboarding.signature;

import static ee.tuleva.onboarding.signature.SignatureFile.SignatureFileType.DIGIDOC_CONTAINER;
import static ee.tuleva.onboarding.signature.SignatureFile.SignatureFileType.HTML;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SignatureFileTest {

  @Test
  void isContainerIsTrueForADigidocContainerFile() {
    var file =
        new SignatureFile("container.asice", DIGIDOC_CONTAINER.getMimeType(), "content".getBytes());

    assertThat(file.isContainer()).isTrue();
  }

  @Test
  void isContainerIsFalseForANonContainerFile() {
    var file = new SignatureFile("document.html", HTML.getMimeType(), "content".getBytes());

    assertThat(file.isContainer()).isFalse();
  }

  @Test
  void constructorFromFileTypeSetsTheMimeTypeFromTheType() {
    var content = "content".getBytes();

    var file = new SignatureFile("document.html", HTML, content);

    assertThat(file.getName()).isEqualTo("document.html");
    assertThat(file.getMimeType()).isEqualTo("text/html");
    assertThat(file.getContent()).isEqualTo(content);
  }
}
