package ee.tuleva.onboarding.mandate.signature;

import com.codeborne.security.mobileid.IdCardSignatureSession;
import com.codeborne.security.mobileid.MobileIDAuthenticator;
import com.codeborne.security.mobileid.SignatureFile;
import ee.sk.smartid.*;
import ee.sk.smartid.rest.dao.NationalIdentity;
import lombok.RequiredArgsConstructor;
import org.apache.commons.codec.binary.Hex;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateEncodingException;
import java.util.List;
import java.util.concurrent.Executor;

@Service
@RequiredArgsConstructor
public class SmartIdSigner {

    private final Executor smartIdExecutor;
    private final SmartIdClient smartIdClient;
    private final MobileIDAuthenticator signer;

    public SmartIdSignatureSession sign(List<SignatureFile> files, String personalCode) {

        NationalIdentity nationalIdentity = new NationalIdentity("EE", personalCode);

        SmartIdCertificate certificateResponse = smartIdClient
                .getCertificate()
                .withNationalIdentity(nationalIdentity)
                .withCertificateLevel("QUALIFIED")
                .fetch();

        IdCardSignatureSession idCardSession;
        try {
            idCardSession = signer.startSign(files, Hex.encodeHexString(certificateResponse.getCertificate().getEncoded()));
        } catch (CertificateEncodingException e) {
            throw new RuntimeException(e);
        }

        MessageDigest messageDigest = getMessageDigest();
        SignableHash signableHash = signableHash(messageDigest.digest(idCardSession.hash.getBytes()));

        SmartIdSignatureSession session = new SmartIdSignatureSession(signableHash.calculateVerificationCode(), idCardSession);

        smartIdExecutor.execute(() -> {
            String documentNumber = certificateResponse.getDocumentNumber();
            SmartIdSignature smartIdSignature = smartIdClient
                    .createSignature()
                    .withDocumentNumber(documentNumber)
                    .withSignableHash(signableHash)
                    .withCertificateLevel("QUALIFIED")
                    .sign();

            String signatureValue = Hex.encodeHexString(smartIdSignature.getValue());
            session.setSignedFile(signer.getSignedFile(idCardSession, signatureValue));
        });

        return session;
    }

    private MessageDigest getMessageDigest() {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
        return digest;
    }

    private SignableHash signableHash(byte[] signatureToSign) {
        SignableHash hashToSign = new SignableHash();
        hashToSign.setHashType(HashType.SHA256);
        hashToSign.setHash(signatureToSign);
        return hashToSign;
    }

    public byte[] getSignedFile(SmartIdSignatureSession session) {
        return session.getSignedFile();
    }
}
