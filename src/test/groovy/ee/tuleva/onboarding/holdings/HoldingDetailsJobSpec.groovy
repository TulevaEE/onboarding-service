package ee.tuleva.onboarding.holdings

import ee.tuleva.onboarding.comparisons.fundvalue.retrieval.globalstock.ftp.FtpClient
import ee.tuleva.onboarding.holdings.models.HoldingDetail
import ee.tuleva.onboarding.holdings.models.Region
import ee.tuleva.onboarding.holdings.models.Sector
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
    private FtpClient ftpClient;

    private static final String PATH = "/Monthly/AllHoldings/XI_MSTAR"

    void setupSpec() {
        fakeFtpServer = new FakeFtpServer()
        fakeFtpServer.addUserAccount(new UserAccount(ftpUsername, ftpPassword, '/'))

        FileSystem fileSystem = new UnixFakeFileSystem()
        fileSystem.add(new DirectoryEntry(PATH))
        fileSystem.add(fakeFileEntry(PATH + "/AllHoldings25_XI_MSTAR_USA_M_20200506.xml.gz", '/morningstar/investment_minimal.xml.gz'))

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
            .styleBox(3)
            .firstBoughtDate(LocalDate.of(2014, 12, 31))
            .createdDate(LocalDate.of(2020, 5, 6))
            .build()

        when:
        job.runJob()

        then:
        1 * repository.save({ it ->
            it.id == detail.id
            it.symbol == detail.symbol
            it.country == detail.country
            it.currency == detail.currency
            it.securityName == detail.securityName
            it.weighting == detail.weighting
            it.firstBoughtDate == detail.firstBoughtDate
            it.createdDate == detail.createdDate
            it.styleBox == detail.styleBox
            it.isin == detail.isin
            it.region == detail.region
            it.holdingYtdReturn == detail.holdingYtdReturn
            it.sector == detail.sector
            it.marketValue == detail.marketValue
            it.shareChange == detail.shareChange
            it.numberOfShare == detail.numberOfShare
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
}
