package ee.tuleva.onboarding.mandate.signature;

import com.codeborne.security.mobileid.SignatureFile;
import ee.sk.smartid.*;
import ee.sk.smartid.exception.SmartIdException;
import ee.sk.smartid.rest.SmartIdConnector;
import ee.sk.smartid.rest.dao.NationalIdentity;
import ee.sk.smartid.rest.dao.SessionStatus;
import ee.tuleva.onboarding.auth.session.GenericSessionStore;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.apache.commons.io.IOUtils;
import org.digidoc4j.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.cert.X509Certificate;
import java.util.List;

import static ee.sk.smartid.HashType.SHA256;

@Service
@RequiredArgsConstructor
public class SmartIdSigner {

    private final SmartIdClient smartIdClient;
    private final SmartIdConnector connector;
    private final GenericSessionStore sessionStore;
    private final Configuration digiDocConfig;

    public SmartIdSignatureSession sign(List<SignatureFile> files, String personalCode) {
        String certificateSessionId = certificateRequestBuilder(personalCode)
            .initiateCertificateChoice();

        return new SmartIdSignatureSession(certificateSessionId, personalCode, files);
    }

    @SneakyThrows
    public byte[] getSignedFile(SmartIdSignatureSession session) {
        SessionStatus certificateSessionStatus = getSessionStatus(session.getCertificateSessionId());
        if (certificateSessionStatus == null) {
            return null;
        }

        if (session.getSigningSessionId() == null) {
            startSigningSession(session, certificateSessionStatus);
            return null;
        }

        SessionStatus signingSessionStatus = getSessionStatus(session.getSigningSessionId());
        if (signingSessionStatus == null) {
            return null;
        }

        return finalizeSignature(session, signingSessionStatus);
    }

    @SneakyThrows
    private byte[] finalizeSignature(SmartIdSignatureSession session, SessionStatus signingSessionStatus) {
        SmartIdSignature smartIdSignature =
            signatureRequestBuilder(session.getSignableHash(), session.getDocumentNumber())
                .createSmartIdSignature(signingSessionStatus);

        byte[] signatureValue = smartIdSignature.getValue();

        DataToSign dataToSign = session.getDataToSign();
        Signature signature = dataToSign.finalize(signatureValue);

        Container container = session.getContainer();
        container.addSignature(signature);

        InputStream containerStream = container.saveAsStream();
        return IOUtils.toByteArray(containerStream);
    }

    @Nullable
    private SessionStatus getSessionStatus(String sessionId) {
        SessionStatus sessionStatus = connector.getSessionStatus(sessionId);
        if (sessionStatus == null || "RUNNING".equalsIgnoreCase(sessionStatus.getState())) {
            return null;
        }
        if (!"COMPLETE".equalsIgnoreCase(sessionStatus.getState())) {
            throw new SmartIdException("Invalid Smart-ID session status: " + sessionStatus.getState());
        }
        return sessionStatus;
    }

    private void startSigningSession(SmartIdSignatureSession session, SessionStatus certificateSessionStatus) {
        SmartIdCertificate smartIdCertificate = certificateRequestBuilder(session.getPersonalCode())
            .createSmartIdCertificate(certificateSessionStatus);

        X509Certificate certificate = smartIdCertificate.getCertificate();
        Container container = buildContainer(session.getFiles());

        DataToSign dataToSign = dataToSign(container, certificate);
        byte[] digestToSign = digestToSign(dataToSign);

        SignableHash signableHash = signableHash(digestToSign);
        String documentNumber = smartIdCertificate.getDocumentNumber();

        String signingSessionId = signatureRequestBuilder(signableHash, documentNumber)
            .initiateSigning();

        session.setChallengeCode(VerificationCodeCalculator.calculate(digestToSign));
        session.setSigningSessionId(signingSessionId);
        session.setDocumentNumber(documentNumber);
        session.setDataToSign(dataToSign);
        session.setSignableHash(signableHash);
        session.setContainer(container);
        sessionStore.save(session);
    }

    private DataToSign dataToSign(Container container, X509Certificate certificate) {
        return SignatureBuilder
            .aSignature(container)
            .withSigningCertificate(certificate)
            .withSignatureDigestAlgorithm(DigestAlgorithm.SHA256)
            .buildDataToSign();
    }

    @SneakyThrows
    private byte[] digestToSign(DataToSign dataToSign) {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return digest.digest(dataToSign.getDataToSign());
    }

    @NotNull
    private SignableHash signableHash(byte[] digestToSign) {
        SignableHash signableHash = new SignableHash();
        signableHash.setHash(digestToSign);
        signableHash.setHashType(SHA256);
        return signableHash;
    }

    private Container buildContainer(List<SignatureFile> files) {
        ContainerBuilder builder = ContainerBuilder
            .aContainer()
            .withConfiguration(digiDocConfig);
        files.forEach(file -> builder.withDataFile(new ByteArrayInputStream(file.content), file.name, file.mimeType));
        return builder.build();
    }

    private CertificateRequestBuilder certificateRequestBuilder(String personalCode) {
        return smartIdClient
            .getCertificate()
            .withNationalIdentity(new NationalIdentity("EE", personalCode))
            .withCertificateLevel("QUALIFIED");
    }

    private SignatureRequestBuilder signatureRequestBuilder(SignableHash signableHash, String documentNumber) {
        return smartIdClient
            .createSignature()
            .withDocumentNumber(documentNumber)
            .withSignableHash(signableHash)
            .withCertificateLevel("QUALIFIED");
    }
}
