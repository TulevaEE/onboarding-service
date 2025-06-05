package ee.tuleva.onboarding.swedbank.http;

import ee.swedbank.gateway.iso.request.Document;
import ee.swedbank.gateway.iso.response.ObjectFactory;
import ee.swedbank.gateway.request.Ping;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.Marshaller;
import jakarta.xml.bind.Unmarshaller;
import java.io.*;
import lombok.SneakyThrows;
import org.springframework.stereotype.Service;

@Service
class SwedbankGatewayMarshaller {

  private final Marshaller marshaller;
  private final Unmarshaller unmarshaller;

  @SneakyThrows
  public String marshalToString(Object object) {
    StringWriter sw = new StringWriter();
    marshaller.marshal(object, sw);
    return sw.toString();
  }

  @SneakyThrows
  public <T> T unMarshal(String response, Class<T> responseClass) {
    return responseClass.cast(unmarshaller.unmarshal(new StringReader(response)));
  }

  @SneakyThrows
  public SwedbankGatewayMarshaller() {
    Class[] requestClasses = {
      Ping.class, Document.class,
    };

    JAXBContext marshalContext = JAXBContext.newInstance(requestClasses);
    this.marshaller = marshalContext.createMarshaller();

    JAXBContext unMarshalContext = JAXBContext.newInstance(ObjectFactory.class);
    this.unmarshaller = unMarshalContext.createUnmarshaller();
  }
}
