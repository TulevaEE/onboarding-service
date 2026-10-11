package ee.tuleva.onboarding.auth.smartid;

import ee.sk.smartid.FlowType;
import ee.sk.smartid.exception.UnprocessableSmartIdResponseException;
import org.jspecify.annotations.Nullable;

public enum SmartIdCompletedFlow {
  QR_CODE,
  SAME_DEVICE,
  NOTIFICATION;

  static SmartIdCompletedFlow of(@Nullable FlowType flowType) {
    return switch (flowType) {
      case QR -> QR_CODE;
      case WEB2APP -> SAME_DEVICE;
      case NOTIFICATION -> NOTIFICATION;
      case null, default ->
          throw new UnprocessableSmartIdResponseException(
              "Smart-ID login completed without a usable flow type: flowType=" + flowType);
    };
  }
}
