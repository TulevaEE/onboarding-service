package ee.tuleva.onboarding.auth.ocsp;

import com.codeborne.security.AuthenticationException;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.cert.jcajce.JcaX509CertificateHolder;
import org.bouncycastle.cert.ocsp.*;
import org.bouncycastle.operator.DigestCalculator;
import org.bouncycastle.operator.DigestCalculatorProvider;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;

import java.io.*;
import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.Security;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

@Slf4j
public class OCSPRequest {
    private final String ocspServer;
    private final String ca;
    private final String method;

    public OCSPRequest(String caToCheck, String ocspServer) {
        this.ca = caToCheck;
        this.ocspServer = ocspServer;
        this.method = "POST";
    }

    public String checkCertificate(X509Certificate certificate){
        if(hasCertificateExpired(certificate)){
            return "CERTIFICATE_EXPIRED";
        }
        return checkSerialNumber(certificate.getSerialNumber());
    }

    private boolean hasCertificateExpired(X509Certificate certificate) {
        return System.currentTimeMillis() > certificate.getNotAfter().getTime();
    }

    public String checkSerialNumber(BigInteger serialNumber) {

        String checkedResponse = null;
        Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());

        try {
            log.info("Generating and sending OCSPRequest");
            OCSPResp response = getOCSPResponse(serialNumber);

            log.info("Validating OCSPResponse");
            checkedResponse = validateOCSPResponse(response);

            log.info("OCSPResponse validated");
        } catch (OCSPException | IOException e) {
            log.warn("Couldn't validate serial number");
            throw new AuthenticationException(AuthenticationException.Code.UNABLE_TO_TEST_USER_CERTIFICATE, "Couldn't validate serial number", e);
        }

        return checkedResponse;
    }


    private OCSPResp getOCSPResponse (BigInteger serialNumber) throws IOException {
        OCSPReq request = generateOCSPRequest(serialNumber);
        OCSPResp response = sendPost(request.getEncoded());
        return response;
    }


    private String validateOCSPResponse(OCSPResp response) throws OCSPException {

        String status = "UNABLE_TO_TEST_USER_CERTIFICATE";
        switch (response.getStatus()) {
            case 0:
                BasicOCSPResp ocspResponseData = (BasicOCSPResp) response.getResponseObject();
                SingleResp[] responses = ocspResponseData.getResponses();
                for (SingleResp response1 : responses) {
                    if (response1.getCertStatus() == null) {
                        status = "GOOD";
                    } else if (response1.getCertStatus() instanceof RevokedStatus) {
                        status = "CERTIFICATE_REVOKED";
                    }
                    log.info("OCSP response code: {0}", status);
                }
                break;
            case 1:
                log.warn("Malformed request. OCSP response code: {0}", response.getStatus());
                throw new AuthenticationException(AuthenticationException.Code.UNABLE_TO_TEST_USER_CERTIFICATE, "Malformed request", new CertificateException());
            default:
                log.warn( "Uncaught error. OCSP response code: {0}", response.getStatus());
                throw new AuthenticationException(AuthenticationException.Code.UNABLE_TO_TEST_USER_CERTIFICATE, "Uncaught error", new CertificateException());
        }
        return status;
    }


    private OCSPReq generateOCSPRequest(BigInteger serial) {

        OCSPReq request = null;
        try {
            CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
            InputStream in = new ByteArrayInputStream(ca.getBytes("UTF-8"));
            X509Certificate issuerCert = (X509Certificate) certFactory.generateCertificate(in);

            JcaDigestCalculatorProviderBuilder digestCalculatorProviderBuilder = new JcaDigestCalculatorProviderBuilder();
            DigestCalculatorProvider digestCalculatorProvider = digestCalculatorProviderBuilder.build();
            DigestCalculator digestCalculator = digestCalculatorProvider.get(CertificateID.HASH_SHA1);

            log.info( "CA subject: {0}", issuerCert.getIssuerX500Principal().getName("CANONICAL"));
            log.info("Serial number: {0}", serial);

            CertificateID id = new CertificateID(digestCalculator, new JcaX509CertificateHolder(issuerCert), serial);
            OCSPReqBuilder ocspGen = new OCSPReqBuilder();
            ocspGen.addRequest(id);

            request = ocspGen.build();
        } catch (CertificateException | OCSPException | OperatorCreationException | UnsupportedEncodingException e) {
            throw new AuthenticationException(AuthenticationException.Code.UNABLE_TO_TEST_USER_CERTIFICATE, "Uncaught error", e);
        }

        return request;
    }

    private OCSPResp sendPost(byte[] request) throws IOException {

        URL url = new URL(ocspServer);

        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setDoOutput(true);
        connection.setDoInput(true);
        connection.setRequestProperty("Content-Type", "application/ocsp-request");
        connection.setRequestProperty("Accept", "application/ocsp-response");
        connection.setRequestProperty("Content-Length", "4096");

        OutputStream output = connection.getOutputStream();
        output.write(request);

        InputStream input = connection.getInputStream();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int bytesRead = 0;
        while ((bytesRead = input.read(buffer, 0, buffer.length)) >= 0) {
            baos.write(buffer, 0, bytesRead);
        }

        log.info("Http response code: {0}", connection.getResponseCode());
        byte[] respBytes = baos.toByteArray();

        return new OCSPResp(respBytes);
    }

}