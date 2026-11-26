package ee.tuleva.onboarding.notification.email;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.time.ZoneOffset.UTC;
import static org.springframework.http.MediaType.APPLICATION_JSON;

import com.google.gson.Gson;
import com.microtripit.mandrillapp.lutung.model.LutungGsonUtils;
import com.microtripit.mandrillapp.lutung.view.MandrillMessage;
import com.microtripit.mandrillapp.lutung.view.MandrillMessageStatus;
import com.microtripit.mandrillapp.lutung.view.MandrillScheduledMessageInfo;
import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.RestClient;

class MandrillClient {

  private static final String SEND_PATH = "/messages/send.json";
  private static final String SEND_TEMPLATE_PATH = "/messages/send-template.json";
  private static final String CANCEL_SCHEDULED_PATH = "/messages/cancel-scheduled.json";
  private static final List<TemplateContent> TEMPLATE_CONTENT_SATISFYING_MANDRILL_VALIDATION =
      List.of(new TemplateContent("satisfy_validation", ""));
  private static final DateTimeFormatter MANDRILL_SEND_AT_FORMAT =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(UTC);
  private static final Gson MANDRILL_JSON = LutungGsonUtils.getGson();

  private final String key;
  private final RestClient whileAUserWaits;
  private final RestClient forABackgroundJob;

  static MandrillClient using(String key, URI mandrillApiUrl) {
    return new MandrillClient(
        key,
        MandrillHttp.restClientWhileAUserWaits(mandrillApiUrl),
        MandrillHttp.restClientForBackgroundJob(mandrillApiUrl));
  }

  MandrillClient(String key, RestClient whileAUserWaits, RestClient forABackgroundJob) {
    this.key = key;
    this.whileAUserWaits = whileAUserWaits;
    this.forABackgroundJob = forABackgroundJob;
  }

  MandrillMessageStatus[] send(MandrillMessage message) {
    var params = paramsWithKey();
    params.put("message", message);
    params.put("async", false);
    return post(forABackgroundJob, SEND_PATH, params, MandrillMessageStatus[].class);
  }

  MandrillMessageStatus[] sendTemplate(
      String templateName, MandrillMessage message, @Nullable Instant sendAt) {
    var params = templateParams(templateName, message);
    if (sendAt != null) {
      params.put("send_at", MANDRILL_SEND_AT_FORMAT.format(sendAt));
    }
    return post(whileAUserWaits, SEND_TEMPLATE_PATH, params, MandrillMessageStatus[].class);
  }

  MandrillMessageStatus[] sendTemplateFromBackgroundJob(
      String templateName, MandrillMessage message) {
    return post(
        forABackgroundJob,
        SEND_TEMPLATE_PATH,
        templateParams(templateName, message),
        MandrillMessageStatus[].class);
  }

  private Map<String, Object> templateParams(String templateName, MandrillMessage message) {
    var params = paramsWithKey();
    params.put("template_name", templateName);
    params.put("template_content", TEMPLATE_CONTENT_SATISFYING_MANDRILL_VALIDATION);
    params.put("message", message);
    params.put("async", false);
    return params;
  }

  MandrillScheduledMessageInfo cancelScheduled(String mandrillMessageId) {
    var params = paramsWithKey();
    params.put("id", mandrillMessageId);
    return post(whileAUserWaits, CANCEL_SCHEDULED_PATH, params, MandrillScheduledMessageInfo.class);
  }

  private Map<String, Object> paramsWithKey() {
    Map<String, Object> params = new LinkedHashMap<>();
    params.put("key", key);
    return params;
  }

  private static <T> T post(
      RestClient restClient, String path, Map<String, Object> params, Class<T> answerType) {
    byte[] answer =
        restClient
            .post()
            .uri(path)
            .contentType(APPLICATION_JSON)
            .body(MANDRILL_JSON.toJson(params).getBytes(UTF_8))
            .retrieve()
            .onStatus(
                status -> status.value() != HttpStatus.OK.value(),
                (request, response) -> {
                  throw refusal(path, response);
                })
            .body(byte[].class);
    @Nullable T parsed =
        answer == null ? null : MANDRILL_JSON.fromJson(new String(answer, UTF_8), answerType);
    if (parsed == null) {
      throw new IllegalStateException("Mandrill answered with an empty body: path=" + path);
    }
    return parsed;
  }

  private static MandrillApiException refusal(String path, ClientHttpResponse response)
      throws IOException {
    return new MandrillApiException(
        path,
        MandrillError.read(
            response.getStatusCode().value(),
            new String(response.getBody().readAllBytes(), UTF_8)));
  }

  private record TemplateContent(String name, String content) {}
}
