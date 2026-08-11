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

    private static final String A_FILE_NAME = "AllHoldings25_XI_MSTAR_USA_M_20200506.xml.gz"

    private static final String TRUNCATED_XML =
        '<Package><PackageBody><InvestmentVehicle _Id="F00000VN9N"><PortfolioList>'

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
