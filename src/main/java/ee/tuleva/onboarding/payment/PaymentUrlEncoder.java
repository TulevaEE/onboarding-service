package ee.tuleva.onboarding.payment;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public final class PaymentUrlEncoder {

  private PaymentUrlEncoder() {}

  public static String encode(Map<String, String> params) {
    var sb = new StringBuilder();
    for (var entry : params.entrySet()) {
      if (!sb.isEmpty()) {
        sb.append('&');
      }
      sb.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8).replace("+", "%20"));
      sb.append('=');
      sb.append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8).replace("+", "%20"));
    }
    return sb.toString();
  }
}
