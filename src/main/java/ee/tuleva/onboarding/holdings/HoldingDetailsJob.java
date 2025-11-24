package ee.tuleva.onboarding.holdings;

import ee.tuleva.onboarding.comparisons.fundvalue.retrieval.globalstock.ftp.FtpClient;
import ee.tuleva.onboarding.holdings.converters.HoldingDetailConverter;
import ee.tuleva.onboarding.holdings.persistence.HoldingDetail;
import ee.tuleva.onboarding.holdings.persistence.HoldingDetailsRepository;
import ee.tuleva.onboarding.holdings.xml.XmlHoldingDetail;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Unmarshaller;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.zip.GZIPInputStream;
import javax.xml.namespace.QName;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.EndElement;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class HoldingDetailsJob {
  private static final String VAN_ID = "F00000VN9N";
  private static final String PATH = "/Monthly/AllHoldings/XI_MSTAR/";

  private final LocalDate initialDate = LocalDate.of(1970, 1, 1);

  private final HoldingDetailsRepository repository;
  private final FtpClient morningstarFtpClient;

  @Scheduled(cron = "0 0 * * * *", zone = "Europe/Tallinn")
  @SchedulerLock(name = "HoldingDetailsJob_runJob", lockAtMostFor = "55m", lockAtLeastFor = "5m")
  public void runJob() {
    log.info("Going to start holding detail job");
    HoldingDetail lastDetail = repository.findFirstByOrderByCreatedDateDesc();
    LocalDate lastDate = initialDate;
    if (lastDetail != null) {
      lastDate = lastDetail.getCreatedDate();
    }

    log.info("Get data from last date: " + lastDate);
    downloadHoldingDetails(lastDate);
  }

  private LocalDate extractFileDate(String fileName) {
    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd");
    int startIndex = fileName.lastIndexOf("_") + 1;
    int endIndex = fileName.length() - 7;
    String fileDateString = fileName.substring(startIndex, endIndex);
    return LocalDate.parse(fileDateString, formatter);
  }

  private void downloadHoldingDetails(LocalDate from) {
    try {
      morningstarFtpClient.open();
      List<String> fileNames = morningstarFtpClient.listFiles(PATH);

      log.info("Retrieved list of files: {}", fileNames);
      for (String fileName : fileNames) {
        if (!fileName.endsWith(".xml.gz")) continue;

        LocalDate fileDate = extractFileDate(fileName);
        log.info("Retrieved a file with date: " + fileDate.toString());

        if (fileDate.compareTo(from) > 0) {
          log.info("Parsing file");
          InputStream fileStream = morningstarFtpClient.downloadFileStream(PATH + fileName);
          GZIPInputStream gzipStream = new GZIPInputStream(fileStream);
          new XmlHoldingDetailParser(
                  gzipStream,
                  VAN_ID,
                  xmlDetail -> {
                    log.info("Parsed entry with security name: " + xmlDetail.getSecurityName());
                    HoldingDetail detail = new HoldingDetailConverter().convert(xmlDetail);
                    detail.setCreatedDate(fileDate);
                    persistHoldingDetail(detail);
                  })
              .parse();
          gzipStream.close();
          morningstarFtpClient.completePendingCommand();
        }
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    } catch (JAXBException | XMLStreamException e) {
      throw new RuntimeException(e);
    }
  }

  private void persistHoldingDetail(HoldingDetail detail) {
    repository.save(detail);
  }

  private static class XmlHoldingDetailParser {
    private final InputStream stream;
    private final String vehicleId;
    private final XmlHoldingDetailListener listener;

    private final QName INVESTMENT_VEHICLE = new QName("InvestmentVehicle");
    private final QName ID_ATTRIBUTE = new QName("_Id");
    private final QName HOLDING_DETAIL = new QName("HoldingDetail");

    private boolean isInVehicle = false;
    private Unmarshaller unmarshaller;

    XmlHoldingDetailParser(InputStream stream, String vehicleId, XmlHoldingDetailListener listener)
        throws JAXBException {
      this.stream = stream;
      this.vehicleId = vehicleId;
      this.listener = listener;

      JAXBContext context = JAXBContext.newInstance(XmlHoldingDetail.class);
      unmarshaller = context.createUnmarshaller();
    }

    private boolean processStartElement(XMLEventReader reader, StartElement e)
        throws JAXBException {
      if (e.getName().equals(INVESTMENT_VEHICLE)
          && e.getAttributeByName(ID_ATTRIBUTE).getValue().equals(vehicleId)) {
        isInVehicle = true;
      } else if (isInVehicle && e.getName().equals(HOLDING_DETAIL)) {
        XmlHoldingDetail holdingDetailEntry =
            unmarshaller.unmarshal(reader, XmlHoldingDetail.class).getValue();
        listener.onNewXmlHoldingDetail(holdingDetailEntry);
        return true;
      }
      return false;
    }

    private boolean processEndElement(XMLEventReader reader, EndElement e) {
      if (e.getName().equals(INVESTMENT_VEHICLE)) {
        isInVehicle = false;
      }
      return false;
    }

    public void parse() throws XMLStreamException, JAXBException {
      XMLInputFactory xmlInputFactory = XMLInputFactory.newInstance();
      XMLEventReader xmlEventReader = xmlInputFactory.createXMLEventReader(stream);
      XMLEvent e = null;

      while ((e = xmlEventReader.peek()) != null) {
        boolean wasCursorChanged = false;
        if (((XMLEvent) e).isStartElement()) {
          wasCursorChanged = processStartElement(xmlEventReader, (StartElement) e);
        } else if (e.isEndElement()) {
          wasCursorChanged = processEndElement(xmlEventReader, (EndElement) e);
        }

        if (!wasCursorChanged) xmlEventReader.next();
      }
    }
  }

  private interface XmlHoldingDetailListener {
    void onNewXmlHoldingDetail(XmlHoldingDetail detail);
  }
}
