package ee.tuleva.onboarding.comparisons.fundvalue.retrieval.globalstock

import ee.tuleva.onboarding.comparisons.fundvalue.FundValue
import ee.tuleva.onboarding.comparisons.fundvalue.retrieval.globalstock.ftp.FtpClient
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

import static ee.tuleva.onboarding.comparisons.fundvalue.retrieval.globalstock.GlobalStockIndexRetriever.KEY
import static java.time.LocalDate.parse

class GlobalIndexValueRetrieverSpec extends Specification {
    @Shared
    private FakeFtpServer fakeFtpServer

    @Shared
    private GlobalStockIndexRetriever retriever

    @Shared
    private String ftpUsername = "someUsername"

    @Shared
    private String ftpPassword = "somePassword"

    @Shared
    private String ftpHost = "localhost"


    private static final String PATH = "/Daily/DMRI/XI_MSTAR"

    void setupSpec() {
        fakeFtpServer = new FakeFtpServer()
        fakeFtpServer.addUserAccount(new UserAccount(ftpUsername, ftpPassword, '/'))

        FileSystem fileSystem = new UnixFakeFileSystem()
        fileSystem.add(new DirectoryEntry(PATH))
        fileSystem.add(fakeFileEntry(PATH + "/DMRI_XI_MSTAR_USA_D_20200325.zip", '/morningstar/DMRI_XI_MSTAR_USA_D_20200325.zip'))
        fileSystem.add(fakeFileEntry(PATH + "/DMRI_XI_MSTAR_USA_D_20200326.zip", '/morningstar/DMRI_XI_MSTAR_USA_D_20200326.zip'))
        fileSystem.add(fakeFileEntry(PATH + "/DMRI_XI_MSTAR_USA_D_20200327.zip", '/morningstar/DMRI_XI_MSTAR_USA_D_20200327.zip'))
        fileSystem.add(fakeFileEntry(PATH + "/DMRI_XI_MSTAR_USA_D_20200330.zip", '/morningstar/DMRI_XI_MSTAR_USA_D_20200330.zip'))
        fileSystem.add(fakeFileEntry(PATH + "/DMRI_XI_MSTAR_USA_D_20200331.zip", '/morningstar/DMRI_XI_MSTAR_USA_D_20200331.zip'))
        fileSystem.add(fakeFileEntry(PATH + "/DMRI_XI_MSTAR_USA_D_20200401.zip",
            '/morningstar/DMRI_XI_MSTAR_USA_D_20200401.zip'))
        fileSystem.add(fakeFileEntry(PATH + "/DMRI_XI_MSTAR_USA_D_20200402.zip",
            '/morningstar/DMRI_XI_MSTAR_USA_D_20200402.zip'))

        fakeFtpServer.setFileSystem(fileSystem)
        fakeFtpServer.setServerControlPort(0)
        fakeFtpServer.start()

        FtpClient ftpClient = new FtpClient(new FTPClient(), ftpHost, ftpUsername, ftpPassword, fakeFtpServer
            .getServerControlPort())

        retriever = new GlobalStockIndexRetriever(ftpClient)
    }

    void cleanupSpec() {
        fakeFtpServer.stop()
    }

    private fakeFileEntry(path, resourceFile) {
        FileEntry entry = new FileEntry(path)
//        print('File Entry')
//        print(resourceFile)
//        print(readFile(resourceFile))
        entry.setContents(readFile(resourceFile))
        return entry
    }

    private byte[] readFile(String fileName) {
        def resource = new ClassPathResource(fileName)
        return Files.readAllBytes(resource.getFile().toPath())
    }

    def "it is configured for the right fund"() {
        when:
        def retrievalFund = retriever.getKey()

        then:
        retrievalFund == KEY
    }

    def "it successfully parses ftp files"() {
        given:
        List<FundValue> expectedValues = [
            new FundValue(KEY, parse("2020-03-26"), 2803.69952),
            new FundValue(KEY, parse("2020-03-27"), 2732.50162),
            new FundValue(KEY, parse("2020-03-28"), 2732.50162),
            new FundValue(KEY, parse("2020-03-29"), 2732.50162),
            new FundValue(KEY, parse("2020-03-30"), 2791.31415),
            new FundValue(KEY, parse("2020-03-31"), 2791.20446),
            new FundValue(KEY, parse("2020-04-01"), 2698.83588),
            new FundValue(KEY, parse("2020-04-02"), 2743.17354),
        ]

        when:
        List<FundValue> values = retriever.retrieveValuesForRange(parse("2020-03-26"), parse("2020-04-02"))

        then:
        values == expectedValues
    }

    def "it skips invalid ftp files"() {
        given:
        List<FundValue> expectedValues = [
            new FundValue(KEY, parse("2020-03-26"), 2803.69952),
            new FundValue(KEY, parse("2020-03-27"), 2732.50162),
            new FundValue(KEY, parse("2020-03-28"), 2732.50162),
            new FundValue(KEY, parse("2020-03-29"), 2732.50162),
            new FundValue(KEY, parse("2020-03-30"), 2791.31415),
            new FundValue(KEY, parse("2020-03-31"), 2791.20446),
        ]

        when:
        List<FundValue> values = retriever.retrieveValuesForRange(parse("2020-03-25"), parse("2020-03-31"))

        then:
        values == expectedValues
    }

    def "it should work with empty data files"() {
        given:
        List<FundValue> expectedValues = [
            new FundValue(KEY, parse("2020-03-26"), 2803.69952),
            new FundValue(KEY, parse("2020-03-27"), 2732.50162),
            new FundValue(KEY, parse("2020-03-28"), 2732.50162),
            new FundValue(KEY, parse("2020-03-29"), 2732.50162),
            new FundValue(KEY, parse("2020-03-30"), 2791.31415),
            new FundValue(KEY, parse("2020-03-31"), 2791.20446),
        ]

        when:
        List<FundValue> values = retriever.retrieveValuesForRange(parse("2020-02-24"), parse("2020-03-31"))

        then:
        values == expectedValues
    }

    def "it should handle ftp client open/close exception"() {
        given:
        FtpClient ftpClient = Mock(FtpClient)
        GlobalStockIndexRetriever retriever = new GlobalStockIndexRetriever(ftpClient)
        ftpClient.open() >> { throw new IOException('123') }
        ftpClient.close() >> { throw new IOException('123') }

        when:
        retriever.retrieveValuesForRange(parse("2020-02-24"), parse("2020-03-31"))

        then:
        noExceptionThrown()
    }

    def "it should handle ftp client download exception"() {
        given:
        FtpClient ftpClient = Mock(FtpClient)
        GlobalStockIndexRetriever retriever = new GlobalStockIndexRetriever(ftpClient)
        ftpClient.listFiles(_ as String) >> { return ['DMRI_XI_MSTAR_USA_D_20200324.zip', 'DMRI_XI_MSTAR_USA_D_20200325.zip'] }
        ftpClient.downloadFileStream(_ as String) >> { throw new IOException('123') }

        when:
        retriever.retrieveValuesForRange(parse("2020-02-24"), parse("2020-03-31"))

        then:
        noExceptionThrown()
    }
}
