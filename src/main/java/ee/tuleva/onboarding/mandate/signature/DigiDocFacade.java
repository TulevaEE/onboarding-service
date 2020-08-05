package ee.tuleva.onboarding.mandate.signature;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.digidoc4j.*;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.security.MessageDigest;
import java.security.cert.X509Certificate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DigiDocFacade {

    private final Configuration digiDocConfig;

    public DataToSign dataToSign(Container container, X509Certificate certificate) {
        return SignatureBuilder
            .aSignature(container)
            .withSigningCertificate(certificate)
            .withSignatureDigestAlgorithm(DigestAlgorithm.SHA256)
            .buildDataToSign();
    }

    @SneakyThrows
    public byte[] digestToSign(DataToSign dataToSign) {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return digest.digest(dataToSign.getDataToSign());
    }

    public Container buildContainer(List<SignatureFile> files) {
        ContainerBuilder builder = ContainerBuilder
            .aContainer()
            .withConfiguration(digiDocConfig);
        files.forEach(file -> builder.withDataFile(new ByteArrayInputStream(file.getContent()), file.getName(),
            file.getMimeType()));
        return builder.build();
    }

}
