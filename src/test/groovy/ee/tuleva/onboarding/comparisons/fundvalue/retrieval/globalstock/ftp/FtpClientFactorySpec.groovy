package ee.tuleva.onboarding.comparisons.fundvalue.retrieval.globalstock.ftp

import org.mockftpserver.fake.FakeFtpServer
import org.mockftpserver.fake.UserAccount
import org.mockftpserver.fake.filesystem.DirectoryEntry
import org.mockftpserver.fake.filesystem.UnixFakeFileSystem
import spock.lang.Specification

class FtpClientFactorySpec extends Specification {

    def "creates a new independent client on every call"() {
        given:
        FtpClientFactory factory = new FtpClientFactory("localhost", "someUsername", "somePassword", 21)

        when:
        FtpClient first = factory.create()
        FtpClient second = factory.create()

        then:
        !first.is(second)
    }

    def "creates clients that can connect"() {
        given:
        FakeFtpServer fakeFtpServer = new FakeFtpServer()
        fakeFtpServer.addUserAccount(new UserAccount("someUsername", "somePassword", '/'))
        def fileSystem = new UnixFakeFileSystem()
        fileSystem.add(new DirectoryEntry("/"))
        fakeFtpServer.setFileSystem(fileSystem)
        fakeFtpServer.setServerControlPort(0)
        fakeFtpServer.start()
        FtpClientFactory factory = new FtpClientFactory(
            "localhost", "someUsername", "somePassword", fakeFtpServer.getServerControlPort())

        when:
        FtpClient client = factory.create()
        client.open()
        def files = client.listFiles("/")

        then:
        files == []

        cleanup:
        client.close()
        fakeFtpServer.stop()
    }
}
