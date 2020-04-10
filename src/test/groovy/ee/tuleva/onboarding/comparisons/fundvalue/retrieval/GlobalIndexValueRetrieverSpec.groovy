package ee.tuleva.onboarding.comparisons.fundvalue.retrieval

import ee.tuleva.onboarding.comparisons.fundvalue.FundValue
import ee.tuleva.onboarding.ftp.FtpClient
import org.mockftpserver.fake.FakeFtpServer
import org.mockftpserver.fake.UserAccount
import org.mockftpserver.fake.filesystem.DirectoryEntry
import org.mockftpserver.fake.filesystem.FileEntry
import org.mockftpserver.fake.filesystem.UnixFakeFileSystem
import org.mockftpserver.fake.filesystem.FileSystem
import org.springframework.core.io.ClassPathResource
import spock.lang.Shared
import spock.lang.Specification

import java.nio.file.Files

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
        fakeFtpServer.addUserAccount(new UserAccount(ftpUsername,ftpPassword, '/'))

        FileSystem fileSystem = new UnixFakeFileSystem()
        fileSystem.add(new DirectoryEntry(PATH))
        fileSystem.add(fakeFileEntry(PATH + "/DMRI_XI_MSTAR_USA_D_20200324.zip", '/morningstar/DMRI_XI_MSTAR_USA_D_20200324.zip'))
        fileSystem.add(fakeFileEntry(PATH + "/DMRI_XI_MSTAR_USA_D_20200325.zip", '/morningstar/DMRI_XI_MSTAR_USA_D_20200325.zip'))
        fileSystem.add(fakeFileEntry(PATH + "/DMRI_XI_MSTAR_USA_D_20200326.zip", '/morningstar/DMRI_XI_MSTAR_USA_D_20200326.zip'))
        fileSystem.add(fakeFileEntry(PATH + "/DMRI_XI_MSTAR_USA_D_20200327.zip", '/morningstar/DMRI_XI_MSTAR_USA_D_20200327.zip'))
        fileSystem.add(fakeFileEntry(PATH + "/DMRI_XI_MSTAR_USA_D_20200330.zip", '/morningstar/DMRI_XI_MSTAR_USA_D_20200330.zip'))
        fileSystem.add(fakeFileEntry(PATH + "/DMRI_XI_MSTAR_USA_D_20200331.zip", '/morningstar/DMRI_XI_MSTAR_USA_D_20200331.zip'))

        fakeFtpServer.setFileSystem(fileSystem)
        fakeFtpServer.setServerControlPort(0)
        fakeFtpServer.start()

        FtpClient ftpClient = new FtpClient(ftpHost, ftpUsername, ftpPassword, fakeFtpServer.getServerControlPort())

        retriever = new GlobalStockIndexRetriever(ftpClient)
    }

    void cleanupSpec() {
        fakeFtpServer.stop()
    }

    private fakeFileEntry(path, resourceFile) {
        FileEntry entry = new FileEntry(path)
        print('File Entry')
        print(resourceFile)
        print(readFile(resourceFile))
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
        retrievalFund == GlobalStockIndexRetriever.KEY
    }

    def "it successfully parses ftp files"() {
        given:
        List<FundValue> expectedValues = [
            new FundValue(GlobalStockIndexRetriever.KEY, parse("2020-03-26"), 2803.69952),
            new FundValue(GlobalStockIndexRetriever.KEY, parse("2020-03-27"), 2732.50162),
            new FundValue(GlobalStockIndexRetriever.KEY, parse("2020-03-28"), 2732.50162),
            new FundValue(GlobalStockIndexRetriever.KEY, parse("2020-03-29"), 2732.50162),
            new FundValue(GlobalStockIndexRetriever.KEY, parse("2020-03-30"), 2791.31415),
            new FundValue(GlobalStockIndexRetriever.KEY, parse("2020-03-31"), 2791.20446),
        ]
        when:
        List<FundValue> values = retriever.retrieveValuesForRange(parse("2020-03-26"), parse("2020-03-31"))

        then:
        values == expectedValues
    }

    def "it skips invalid ftp files"() {
        given:
        List<FundValue> expectedValues = [
            new FundValue(GlobalStockIndexRetriever.KEY, parse("2020-03-26"), 2803.69952),
            new FundValue(GlobalStockIndexRetriever.KEY, parse("2020-03-27"), 2732.50162),
            new FundValue(GlobalStockIndexRetriever.KEY, parse("2020-03-28"), 2732.50162),
            new FundValue(GlobalStockIndexRetriever.KEY, parse("2020-03-29"), 2732.50162),
            new FundValue(GlobalStockIndexRetriever.KEY, parse("2020-03-30"), 2791.31415),
            new FundValue(GlobalStockIndexRetriever.KEY, parse("2020-03-31"), 2791.20446),
        ]
        when:
        List<FundValue> values = retriever.retrieveValuesForRange(parse("2020-03-25"), parse("2020-03-31"))

        then:
        values == expectedValues
    }

    def "it should work with empty data files"() {
        given:
        List<FundValue> expectedValues = [
            new FundValue(GlobalStockIndexRetriever.KEY, parse("2020-03-26"), 2803.69952),
            new FundValue(GlobalStockIndexRetriever.KEY, parse("2020-03-27"), 2732.50162),
            new FundValue(GlobalStockIndexRetriever.KEY, parse("2020-03-28"), 2732.50162),
            new FundValue(GlobalStockIndexRetriever.KEY, parse("2020-03-29"), 2732.50162),
            new FundValue(GlobalStockIndexRetriever.KEY, parse("2020-03-30"), 2791.31415),
            new FundValue(GlobalStockIndexRetriever.KEY, parse("2020-03-31"), 2791.20446),
        ]
        when:
        List<FundValue> values = retriever.retrieveValuesForRange(parse("2020-03-24"), parse("2020-03-31"))

        then:
        values == expectedValues
    }
}
