package ee.tuleva.onboarding.mandate.signature;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.cert.X509Certificate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.apache.commons.io.IOUtils;
import org.digidoc4j.*;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DigiDocFacade {

  private final Configuration digiDocConfig;

  public Container buildContainer(List<SignatureFile> files) {
    if (isExistingContainer(files)) {
      return buildContainerFromExistingContainer(files.getFirst());
    }

    ContainerBuilder builder = ContainerBuilder.aContainer().withConfiguration(digiDocConfig);
    files.forEach(
        file ->
            builder.withDataFile(
                new ByteArrayInputStream(file.getContent()), file.getName(), file.getMimeType()));
    return builder.build();
  }

  private Container buildContainerFromExistingContainer(SignatureFile containerFile) {
    if (!containerFile.isContainer()) {
      throw new IllegalArgumentException("File is not an existing container");
    }

    ContainerBuilder builder = ContainerBuilder.aContainer().withConfiguration(digiDocConfig);
    builder.fromStream(new ByteArrayInputStream(containerFile.getContent()));
    return builder.build();
  }

  // This has the unfortunate side effect that containers with a single zip archive are not
  // signable,
  // but since the container itself is a zip archive which can hold multiple files,
  // it seems like a very small edge case
  private boolean isExistingContainer(List<SignatureFile> files) {
    if (files.size() != 1) {
      return false;
    }

    return files.getFirst().isContainer();
  }

  public DataToSign dataToSign(Container container, X509Certificate certificate) {
    return SignatureBuilder.aSignature(container)
        .withSigningCertificate(certificate)
        .withSignatureDigestAlgorithm(DigestAlgorithm.SHA256)
        .buildDataToSign();
  }

  @SneakyThrows
  public byte[] digestToSign(DataToSign dataToSign) {
    MessageDigest digest = MessageDigest.getInstance("SHA-256");
    return digest.digest(dataToSign.getDataToSign());
  }

  @SneakyThrows
  public byte[] addSignatureToContainer(
      byte[] signatureValue, DataToSign dataToSign, Container container) {
    Signature signature = dataToSign.finalize(signatureValue);
    container.addSignature(signature);
    InputStream containerStream = container.saveAsStream();
    return IOUtils.toByteArray(containerStream);
  }
}
