package ee.tuleva.onboarding.holdings

import ee.tuleva.onboarding.comparisons.fundvalue.retrieval.globalstock.ftp.FtpClient
import ee.tuleva.onboarding.holdings.persistence.HoldingDetail
import ee.tuleva.onboarding.holdings.persistence.Region
import ee.tuleva.onboarding.holdings.persistence.Sector
import ee.tuleva.onboarding.holdings.persistence.HoldingDetailsRepository
import org.apache.commons.net.ftp.FTPClient
import org.mockftpserver.fake.FakeFtpServer
import org.mockftpserver.fake.UserAccount
import org.mockftpserver.fake.filesystem.DirectoryEntry
import org.mockftpserver.fake.filesystem.FileEntry
import org.mockftpserver.fake.filesystem.FileSystem
import org.mockftpserver.fake.filesystem.UnixFakeFileSystem
import org.springframework.core.io.ClassPathResource
import spock.lang.Shared
import spock.lang.Specification

import java.nio.file.Files
import java.time.LocalDate
import java.util.zip.GZIPOutputStream

class HoldingDetailsJobSpec extends Specification {
    @Shared
    private FakeFtpServer fakeFtpServer

    HoldingDetailsRepository repository = Mock(HoldingDetailsRepository)

    HoldingDetailsJob job = new HoldingDetailsJob(repository, ftpClient)

    @Shared
    private String ftpUsername = "someUsername"

    @Shared
    private String ftpPassword = "somePassword"

    @Shared
    private String ftpHost = "localhost"

    @Shared
    private FtpClient ftpClient

    private static final String PATH = "/Monthly/AllHoldings/XI_MSTAR"

    void setupSpec() {
        fakeFtpServer = new FakeFtpServer()
        fakeFtpServer.addUserAccount(new UserAccount(ftpUsername, ftpPassword, '/'))

        FileSystem fileSystem = new UnixFakeFileSystem()
        fileSystem.add(new DirectoryEntry(PATH))
        fileSystem.add(fakeFileEntry(
            PATH + "/AllHoldings25_XI_MSTAR_USA_M_20200506.xml.gz",
            '/morningstar/investment_minimal.xml.gz'
        ))

        fakeFtpServer.setFileSystem(fileSystem)
        fakeFtpServer.setServerControlPort(0)
        fakeFtpServer.start()

        ftpClient = new FtpClient(new FTPClient(), ftpHost, ftpUsername, ftpPassword, fakeFtpServer
            .getServerControlPort())
    }

    void cleanupSpec() {
        fakeFtpServer.stop()
    }

    private fakeFileEntry(path, resourceFile) {
        FileEntry entry = new FileEntry(path)
        entry.setContents(readFile(resourceFile))
        return entry
    }

    private byte[] readFile(String fileName) {
        def resource = new ClassPathResource(fileName)
        return Files.readAllBytes(resource.getFile().toPath())
    }

    def "should be able to reuse ftp client"() {
        given:
        ftpClient.close()
        repository.findFirstByOrderByCreatedDateDesc() >> null

        when:
        job.runJob()

        then:
        1 * repository.save(_)
    }

    def "should persist holding detail if no entry exist"() {
        given:
        repository.findFirstByOrderByCreatedDateDesc() >> null
        HoldingDetail detail = HoldingDetail.builder()
            .symbol("MSFT")
            .country("USA")
            .currency("USD")
            .securityName("Microsoft Corp")
            .weighting(2.76)
            .numberOfShare(7628806000)
            .shareChange(0)
            .marketValue(1367158323260)
            .sector(Sector.valueOf(11))
            .holdingYtdReturn(11.02)
            .region(Region.valueOf(1))
            .isin("US5949181045")
            .firstBoughtDate(LocalDate.of(2014, 12, 31))
            .createdDate(LocalDate.of(2020, 5, 6))
            .build()

        when:
        job.runJob()

        then:
        1 * repository.save({ it ->
            it == detail
        })
    }

    def "should persist holding detail if last entry is not up to date"() {
        given:
        LocalDate oldDate = LocalDate.of(2019, 5, 4)
        HoldingDetail detail = HoldingDetail.builder()
            .id(1)
            .createdDate(oldDate)
            .build()

        repository.findFirstByOrderByCreatedDateDesc() >> detail

        when:
        job.runJob()

        then:
        1 * repository.save(_ as HoldingDetail)
    }

    def "should not persist holding detail if last entry is up to date"() {
        given:
        LocalDate latestDate = LocalDate.of(2020, 5, 6)
        HoldingDetail detail = HoldingDetail.builder()
            .id(1)
            .createdDate(latestDate)
            .build()

        repository.findFirstByOrderByCreatedDateDesc() >> detail

        when:
        job.runJob()

        then:
        0 * repository.save(_ as HoldingDetail)
    }

    def "fails fast when the ftp download returns no stream"() {
        given:
        FtpClient unreadyClient = Mock(FtpClient)
        HoldingDetailsJob jobUnderTest = new HoldingDetailsJob(repository, unreadyClient)
        repository.findFirstByOrderByCreatedDateDesc() >> null
        unreadyClient.listFiles(_) >> [A_FILE_NAME]
        unreadyClient.downloadFileStream(_) >> null

        when:
        jobUnderTest.runJob()

        then:
        thrown(IllegalStateException)
    }

    def "closes the ftp session when parsing fails"() {
        given:
        FtpClient failingClient = Mock(FtpClient)
        HoldingDetailsJob jobUnderTest = new HoldingDetailsJob(repository, failingClient)
        repository.findFirstByOrderByCreatedDateDesc() >> null
        failingClient.listFiles(_) >> [A_FILE_NAME]
        failingClient.downloadFileStream(_) >> gzipOf(TRUNCATED_XML)

        when:
        jobUnderTest.runJob()

        then:
        thrown(RuntimeException)
        1 * failingClient.close()
        0 * repository.save(_)
    }

    def "fails when the transfer does not complete"() {
        given:
        FtpClient truncatingClient = Mock(FtpClient)
        HoldingDetailsJob jobUnderTest = new HoldingDetailsJob(repository, truncatingClient)
        repository.findFirstByOrderByCreatedDateDesc() >> null
        truncatingClient.listFiles(_) >> [A_FILE_NAME]
        truncatingClient.downloadFileStream(_) >> gzipOf(readFileAsString('/morningstar/investment_minimal.xml.gz'))
        truncatingClient.completePendingCommand() >> false

        when:
        jobUnderTest.runJob()

        then:
        thrown(IllegalStateException)
        1 * truncatingClient.close()
        0 * repository.save(_)
    }

    def "closes the downloaded stream when gzip initialisation fails"() {
        given:
        FtpClient corruptClient = Mock(FtpClient)
        HoldingDetailsJob jobUnderTest = new HoldingDetailsJob(repository, corruptClient)
        def downloadedStream = new ClosingTrackingInputStream("this is not gzipped at all".bytes)
        repository.findFirstByOrderByCreatedDateDesc() >> null
        corruptClient.listFiles(_) >> [A_FILE_NAME]
        corruptClient.downloadFileStream(_) >> downloadedStream

        when:
        jobUnderTest.runJob()

        then:
        thrown(RuntimeException)
        downloadedStream.closed
        1 * corruptClient.close()
    }

    def "parses a file whose accumulated entity references exceed the jdk default limit"() {
        given:
        FtpClient bigFileClient = Mock(FtpClient)
        HoldingDetailsJob jobUnderTest = new HoldingDetailsJob(repository, bigFileClient)
        repository.findFirstByOrderByCreatedDateDesc() >> null
        bigFileClient.listFiles(_) >> [A_FILE_NAME]
        bigFileClient.downloadFileStream(_) >> gzipOf(xmlWithEntityReferencesOverJdkLimit())
        bigFileClient.completePendingCommand() >> true

        when:
        jobUnderTest.runJob()

        then:
        300 * repository.save(_)
    }

    def "does not resolve external entities"() {
        given:
        def externalFile = File.createTempFile("holdings-external-entity", ".txt")
        externalFile.text = "INJECTED"
        FtpClient hostileClient = Mock(FtpClient)
        HoldingDetailsJob jobUnderTest = new HoldingDetailsJob(repository, hostileClient)
        repository.findFirstByOrderByCreatedDateDesc() >> null
        hostileClient.listFiles(_) >> [A_FILE_NAME]
        hostileClient.downloadFileStream(_) >> gzipOf(xmlWithExternalEntity(externalFile.toURI().toString()))
        hostileClient.completePendingCommand() >> true

        when:
        jobUnderTest.runJob()

        then:
        1 * repository.save({ it.securityName == "prepost" })

        cleanup:
        externalFile.delete()
    }

    def "does not read external dtds"() {
        given:
        def externalDtd = File.createTempFile("holdings-external", ".dtd")
        externalDtd.text = ""
        FtpClient hostileClient = Mock(FtpClient)
        HoldingDetailsJob jobUnderTest = new HoldingDetailsJob(repository, hostileClient)
        repository.findFirstByOrderByCreatedDateDesc() >> null
        hostileClient.listFiles(_) >> [A_FILE_NAME]
        hostileClient.downloadFileStream(_) >> gzipOf(xmlWithExternalDtd(externalDtd.toURI().toString()))
        hostileClient.completePendingCommand() >> true

        when:
        jobUnderTest.runJob()

        then:
        thrown(RuntimeException)
        0 * repository.save(_)

        cleanup:
        externalDtd.delete()
    }

    def "rejects a file with runaway internal entity expansion"() {
        given:
        FtpClient hostileClient = Mock(FtpClient)
        HoldingDetailsJob jobUnderTest = new HoldingDetailsJob(repository, hostileClient)
        repository.findFirstByOrderByCreatedDateDesc() >> null
        hostileClient.listFiles(_) >> [A_FILE_NAME]
        hostileClient.downloadFileStream(_) >> gzipOf(xmlWithRunawayEntityExpansion())
        hostileClient.completePendingCommand() >> true

        when:
        jobUnderTest.runJob()

        then:
        thrown(RuntimeException)
        0 * repository.save(_)
    }

    private static class ClosingTrackingInputStream extends ByteArrayInputStream {
        boolean closed = false

        ClosingTrackingInputStream(byte[] bytes) {
            super(bytes)
        }

        @Override
        void close() throws IOException {
            closed = true
            super.close()
        }
    }

    private static final String A_FILE_NAME = "AllHoldings25_XI_MSTAR_USA_M_20200506.xml.gz"

    private static final String TRUNCATED_XML =
        '<Package><PackageBody><InvestmentVehicle _Id="F00000VN9N"><PortfolioList>'

    private static String xmlWithEntityReferencesOverJdkLimit() {
        def securityName = 'Procter ' + '&amp; Gamble ' * 350
        def holding = """<HoldingDetail _ExternalId="742718109" _Id="E0USA002UJ">
            <Symbol>PG</Symbol>
            <Country _Id="USA">United States</Country>
            <Currency _Id="USD">US Dollar</Currency>
            <SecurityName>${securityName}</SecurityName>
            <Weighting>2.76</Weighting>
            <NumberOfShare>7628806000</NumberOfShare>
            <ShareChange>0</ShareChange>
            <MarketValue>1367158323260</MarketValue>
            <Sector>11</Sector>
            <HoldingYTDReturn>11.02</HoldingYTDReturn>
            <Region>1</Region>
            <ISIN>US7427181091</ISIN>
            <FirstBoughtDate>2014-12-31</FirstBoughtDate>
        </HoldingDetail>"""
        return '<Package><PackageBody><InvestmentVehicle _Id="F00000VN9N"><PortfolioList><Portfolio><Holding>' +
            holding * 300 +
            '</Holding></Portfolio></PortfolioList></InvestmentVehicle></PackageBody></Package>'
    }

    private static String xmlWithExternalEntity(String externalUri) {
        return """<?xml version="1.0"?>
            <!DOCTYPE Package [<!ENTITY ext SYSTEM "${externalUri}">]>
            <Package><PackageBody><InvestmentVehicle _Id="F00000VN9N"><PortfolioList><Portfolio><Holding>
            <HoldingDetail _ExternalId="742718109" _Id="E0USA002UJ">
                <SecurityName>pre&ext;post</SecurityName>
                <Weighting>2.76</Weighting>
                <Sector>11</Sector>
                <Region>1</Region>
                <FirstBoughtDate>2014-12-31</FirstBoughtDate>
            </HoldingDetail>
            </Holding></Portfolio></PortfolioList></InvestmentVehicle></PackageBody></Package>"""
    }

    private static String xmlWithExternalDtd(String dtdUri) {
        return """<?xml version="1.0"?>
            <!DOCTYPE Package SYSTEM "${dtdUri}">
            <Package><PackageBody><InvestmentVehicle _Id="F00000VN9N"><PortfolioList><Portfolio><Holding>
            <HoldingDetail _ExternalId="742718109" _Id="E0USA002UJ">
                <SecurityName>Procter Gamble</SecurityName>
                <Weighting>2.76</Weighting>
                <Sector>11</Sector>
                <Region>1</Region>
                <FirstBoughtDate>2014-12-31</FirstBoughtDate>
            </HoldingDetail>
            </Holding></Portfolio></PortfolioList></InvestmentVehicle></PackageBody></Package>"""
    }

    private static String xmlWithRunawayEntityExpansion() {
        return """<?xml version="1.0"?>
            <!DOCTYPE Package [<!ENTITY a "x">]>
            <Package><PackageBody><InvestmentVehicle _Id="F00000VN9N"><PortfolioList><Portfolio><Holding>
            <HoldingDetail _ExternalId="742718109" _Id="E0USA002UJ">
                <SecurityName>${'&a;' * 2501}</SecurityName>
                <Weighting>2.76</Weighting>
                <Sector>11</Sector>
                <Region>1</Region>
                <FirstBoughtDate>2014-12-31</FirstBoughtDate>
            </HoldingDetail>
            </Holding></Portfolio></PortfolioList></InvestmentVehicle></PackageBody></Package>"""
    }

    private static InputStream gzipOf(String xml) {
        def compressed = new ByteArrayOutputStream()
        new GZIPOutputStream(compressed).withCloseable { it.write(xml.getBytes("UTF-8")) }
        return new ByteArrayInputStream(compressed.toByteArray())
    }

    private String readFileAsString(String fileName) {
        return new String(gunzip(readFile(fileName)), "UTF-8")
    }

    private static byte[] gunzip(byte[] compressed) {
        return new java.util.zip.GZIPInputStream(new ByteArrayInputStream(compressed)).bytes
    }
}
