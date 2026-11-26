package ee.tuleva.onboarding.notification.email;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.microtripit.mandrillapp.lutung.model.LutungGsonUtils;
import java.util.Set;
import org.jspecify.annotations.Nullable;

record MandrillError(
    int httpStatus, @Nullable Integer code, @Nullable String name, @Nullable String message) {

  private static final Set<String> MANDRILL_ERRORS_BEFORE_SENDING =
      Set.of("Invalid_Key", "PaymentRequired", "Unknown_Subaccount", "ValidationError");
  private static final Gson MANDRILL_JSON = LutungGsonUtils.getGson();
  private static final NameAndMessage UNREADABLE = new NameAndMessage(null, null);

  static MandrillError read(int httpStatus, String body) {
    var nameAndMessage = nameAndMessageOf(body);
    return new MandrillError(
        httpStatus, codeOf(body), nameAndMessage.name(), nameAndMessage.message());
  }

  boolean wasRaisedBeforeSending() {
    var errorName = name;
    return errorName != null && MANDRILL_ERRORS_BEFORE_SENDING.contains(errorName);
  }

  private static NameAndMessage nameAndMessageOf(String body) {
    try {
      var nameAndMessage = MANDRILL_JSON.fromJson(body, NameAndMessage.class);
      return nameAndMessage == null ? UNREADABLE : nameAndMessage;
    } catch (RuntimeException e) {
      return UNREADABLE;
    }
  }

  private static @Nullable Integer codeOf(String body) {
    try {
      JsonElement code = JsonParser.parseString(body).getAsJsonObject().get("code");
      return isNumber(code) ? code.getAsBigDecimal().intValueExact() : null;
    } catch (RuntimeException e) {
      return null;
    }
  }

  private static boolean isNumber(@Nullable JsonElement field) {
    return field != null && field.isJsonPrimitive() && field.getAsJsonPrimitive().isNumber();
  }

  private record NameAndMessage(@Nullable String name, @Nullable String message) {}
}
