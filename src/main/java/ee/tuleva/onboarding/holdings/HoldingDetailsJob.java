package ee.tuleva.onboarding.holdings;

import static javax.xml.XMLConstants.ACCESS_EXTERNAL_DTD;
import static javax.xml.stream.XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES;

import ee.tuleva.onboarding.comparisons.fundvalue.retrieval.globalstock.ftp.FtpClient;
import ee.tuleva.onboarding.comparisons.fundvalue.retrieval.globalstock.ftp.FtpClientFactory;
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
import java.util.ArrayList;
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
  private final FtpClientFactory morningstarFtpClientFactory;

  @Scheduled(cron = "0 0 * * * *", zone = "Europe/Tallinn")
  @SchedulerLock(name = "HoldingDetailsJob_runJob", lockAtMostFor = "55m", lockAtLeastFor = "5m")
  public void runJob() {
    log.info("Going to start holding detail job");
    HoldingDetail lastDetail = repository.findFirstByOrderByCreatedDateDesc();
    LocalDate lastDate = initialDate;
    if (lastDetail != null) {
      lastDate = lastDetail.getCreatedDate();
    }

    log.info("Getting holding details: fromDate={}", lastDate);
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
    FtpClient ftpClient = morningstarFtpClientFactory.create();
    try {
      ftpClient.open();
      List<String> fileNames = ftpClient.listFiles(PATH);

      log.info("Retrieved list of files: {}", fileNames);
      for (String fileName : fileNames) {
        if (!fileName.endsWith(".xml.gz")) continue;

        LocalDate fileDate = extractFileDate(fileName);
        log.info("Retrieved a file: fileName={}, fileDate={}", fileName, fileDate);

        if (fileDate.compareTo(from) > 0) {
          parseHoldingDetailsFile(ftpClient, fileName, fileDate);
        }
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    } catch (JAXBException | XMLStreamException e) {
      throw new RuntimeException(e);
    } finally {
      closeFtpConnection(ftpClient);
    }
  }

  private void parseHoldingDetailsFile(FtpClient ftpClient, String fileName, LocalDate fileDate)
      throws IOException, JAXBException, XMLStreamException {
    log.info("Parsing file: fileName={}", fileName);
    InputStream fileStream = ftpClient.downloadFileStream(PATH + fileName);

    if (fileStream == null) {
      throw new IllegalStateException("FTP download returned no stream: path=" + PATH + fileName);
    }

    List<HoldingDetail> parsedDetails = new ArrayList<>();
    try (InputStream downloadedStream = fileStream;
        GZIPInputStream gzipStream = new GZIPInputStream(downloadedStream)) {
      new XmlHoldingDetailParser(
              gzipStream,
              VAN_ID,
              xmlDetail -> {
                log.debug("Parsed entry: securityName={}", xmlDetail.getSecurityName());
                HoldingDetail detail = new HoldingDetailConverter().convert(xmlDetail);
                detail.setCreatedDate(fileDate);
                parsedDetails.add(detail);
              })
          .parse();
    }

    if (!ftpClient.completePendingCommand()) {
      throw new IllegalStateException("FTP transfer did not complete: path=" + PATH + fileName);
    }

    parsedDetails.forEach(this::persistHoldingDetail);
  }

  private void closeFtpConnection(FtpClient ftpClient) {
    try {
      ftpClient.close();
    } catch (IOException e) {
      log.error("Unable to close FTP connection: path={}", PATH, e);
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
      xmlInputFactory.setProperty(ACCESS_EXTERNAL_DTD, "");
      xmlInputFactory.setProperty(IS_SUPPORTING_EXTERNAL_ENTITIES, false);
      xmlInputFactory.setProperty("jdk.xml.entityExpansionLimit", 2_500);
      int maxAccumulatedEntitySize = 2_000_000;
      xmlInputFactory.setProperty("jdk.xml.maxGeneralEntitySizeLimit", maxAccumulatedEntitySize);
      xmlInputFactory.setProperty("jdk.xml.totalEntitySizeLimit", maxAccumulatedEntitySize);
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
