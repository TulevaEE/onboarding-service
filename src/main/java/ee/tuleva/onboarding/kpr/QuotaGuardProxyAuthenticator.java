package ee.tuleva.onboarding.kpr;


import java.net.Authenticator;
import java.net.MalformedURLException;
import java.net.PasswordAuthentication;
import java.net.URL;

/**
 * http://support.quotaguard.com/support/solutions/articles/5000013914-java-quick-start-guide-quotaguard-static
 */
public class QuotaGuardProxyAuthenticator extends Authenticator{
    private String user, password, host;
    private int port;
    private ProxyAuthenticator auth;

    public QuotaGuardProxyAuthenticator() {
        String proxyUrlEnv = System.getenv("QUOTAGUARDSTATIC_URL");

        if (proxyUrlEnv != null) {
            try {
                URL proxyUrl = new URL(proxyUrlEnv);
                String authString = proxyUrl.getUserInfo();
                user = authString.split(":")[0];
                password = authString.split(":")[1];
                host = proxyUrl.getHost();
                port = proxyUrl.getPort();
                auth = new ProxyAuthenticator(user,password);
                setProxy();
            } catch (MalformedURLException e) {
                throw new RuntimeException(e);
            }
        } else {
            System.err.println("You need to set the environment variable QUOTAGUARDSTATIC_URL!");
        }

    }

    private void setProxy() {
        // https://bugs.openjdk.java.net/browse/JDK-8168839
        System.setProperty("jdk.http.auth.tunneling.disabledSchemes","");

        System.setProperty("http.proxyHost", host);
        System.setProperty("http.proxyPort", String.valueOf(port));
        System.setProperty("https.proxyHost",host);
        System.setProperty("https.proxyPort", String.valueOf(port));

        Authenticator.setDefault(this.auth);
    }

    public String getEncodedAuth(){
        //If not using Java8 you will have to use another Base64 encoded, e.g. apache commons codec.
        return java.util.Base64.getEncoder().encodeToString((user + ":" + password).getBytes());
    }

    public ProxyAuthenticator getAuth(){
        return auth;
    }

    class ProxyAuthenticator extends Authenticator {

        private String user, password;

        public ProxyAuthenticator(String user, String password) {
            this.user = user;
            this.password = password;
        }

        protected PasswordAuthentication getPasswordAuthentication() {
            return new PasswordAuthentication(user, password.toCharArray());
        }
    }

}
