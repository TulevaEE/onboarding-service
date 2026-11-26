package ee.tuleva.onboarding.notification.email

import org.springframework.web.client.ResourceAccessException
import spock.lang.Specification

import java.net.http.HttpConnectTimeoutException
import java.net.http.HttpTimeoutException

class MandrillHttpSpec extends Specification {

  def "a failure that never reached Mandrill is recognised anywhere in the cause chain: #failure"() {
    expect:
    MandrillHttp.neverReachedMandrill(failure)

    where:
    failure << [
        new ConnectException("Connection refused"),
        new UnknownHostException("mandrill.example.com"),
        new HttpConnectTimeoutException("HTTP connect timed out"),
        new ResourceAccessException("I/O error", new ConnectException("Connection refused")),
        new ResourceAccessException("I/O error", new UnknownHostException("mandrill.example.com")),
        new ResourceAccessException("I/O error", new HttpConnectTimeoutException("HTTP connect timed out"))]
  }

  def "a failure that may have reached Mandrill is not taken for one that never did: #failure"() {
    expect:
    !MandrillHttp.neverReachedMandrill(failure)

    where:
    failure << [
        new HttpTimeoutException("request timed out"),
        new SocketTimeoutException("Read timed out"),
        new IOException("Connection reset"),
        new ResourceAccessException("I/O error", new HttpTimeoutException("request timed out")),
        new ResourceAccessException("I/O error"),
        new IllegalStateException("Mandrill answered with an empty body")]
  }
}
