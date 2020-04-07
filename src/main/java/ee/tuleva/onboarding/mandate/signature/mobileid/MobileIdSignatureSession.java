package ee.tuleva.onboarding.mandate.signature.mobileid;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.digidoc4j.Container;
import org.digidoc4j.DataToSign;

@Getter
public class MobileIdSignatureSession implements Serializable {

  private static final long serialVersionUID = -7443368341567864757L;

  private String sessionID;
  private String verificationCode;
  private DataToSign dataToSign;
  private Container container;
  @Setter private List<String> errors = new ArrayList<>();

  private MobileIdSignatureSession(Builder builder) {
    this.sessionID = builder.sessionID;
    this.verificationCode = builder.verificationCode;
    this.dataToSign = builder.dataToSign;
    this.container = builder.container;
  }

  public static Builder newBuilder() {
    return new Builder();
  }

  public static class Builder {
    private String sessionID;
    private String verificationCode;
    private DataToSign dataToSign;
    private Container container;

    private Builder() {}

    public Builder withSessionID(String sessionID) {
      this.sessionID = sessionID;
      return this;
    }

    public Builder withVerificationCode(String verificationCode) {
      this.verificationCode = verificationCode;
      return this;
    }

    public Builder withDataToSign(DataToSign dataToSign) {
      this.dataToSign = dataToSign;
      return this;
    }

    public Builder withContainer(Container container) {
      this.container = container;
      return this;
    }

    public MobileIdSignatureSession build() {
      return new MobileIdSignatureSession(this);
    }
  }
}
