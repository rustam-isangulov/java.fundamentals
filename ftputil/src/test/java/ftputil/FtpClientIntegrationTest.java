package ftputil;

import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.mockftpserver.core.command.ConnectCommandHandler;
import org.mockftpserver.fake.FakeFtpServer;
import org.mockftpserver.fake.UserAccount;
import org.mockftpserver.fake.filesystem.DirectoryEntry;
import org.mockftpserver.fake.filesystem.FileEntry;
import org.mockftpserver.fake.filesystem.FileSystem;
import org.mockftpserver.fake.filesystem.UnixFakeFileSystem;

import java.io.*;
import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

public class FtpClientIntegrationTest {

    private final Path fileOnePath = Path.of("file_one.json");
    private final Path fileTwoPath = Path.of("file_two_json");

    private final String fileOneContent = "{\"id\":\"1\",\"approvedSymbol\":\"AAA\"}";
    private final String fileTwoContent = "{\"id\":\"2\",\"approvedSymbol\":\"BBB\"}";

    @Nested
    @DisplayName("Given that an FTP server with accessible content is available")
    class testWithFTPServerAvailable {

        private final URI server = URI.create("localhost");
        private final Path remoteBase = Path.of("/pub/");
        private final Path dataDir = Path.of("data");

        private FakeFtpServer fakeFtpServer;

        @BeforeEach
        public void setUp() {

            // setup ftp server
            fakeFtpServer = new FakeFtpServer();

            // setup default account
            UserAccount anonymous = new UserAccount();
            anonymous.setPasswordRequiredForLogin(false);
            anonymous.setUsername("anonymous");
            anonymous.setHomeDirectory("/");

            fakeFtpServer.addUserAccount(anonymous);

            // setup sample directory and files structure
            FileSystem fileSystem = new UnixFakeFileSystem();
            fileSystem.add(new DirectoryEntry(remoteBase.toString()));
            fileSystem.add(new FileEntry(remoteBase.resolve(dataDir.resolve(fileOnePath)).toString(), fileOneContent));
            fileSystem.add(new FileEntry(remoteBase.resolve(dataDir.resolve(fileTwoPath)).toString(), fileTwoContent));

            fakeFtpServer.setFileSystem(fileSystem);
            fakeFtpServer.setSystemName("Unix");

            fakeFtpServer.start();
        }

        @AfterEach
        public void tearDown() {
            fakeFtpServer.stop();
        }

        @Nested
        @DisplayName("When we connect with a new FtpClient instance")
        public class testConnected {

            @Test
            @DisplayName("Then we can list files in a remote directory")
            public void testListOfFiles() {

                try (var ftpClient = FtpClient.getClient(server, new FTPClient())) {
                    List<FTPFile> files = ftpClient.listFiles(remoteBase.resolve(dataDir));

                    assertLinesMatch(List.of(fileOnePath.toString(), fileTwoPath.toString())
                            , files.stream().map(FTPFile::getName).collect(Collectors.toList()));

                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }

            @Test
            @DisplayName("Then we can download content of all remote files in a directory")
            public void testDownloadFiles() {

                // create output streams for the content of remote files
                Map<Path, ByteArrayOutputStream> outputStreams = Map.of
                        (fileOnePath, new ByteArrayOutputStream()
                        , fileTwoPath, new ByteArrayOutputStream());

                // prepare outputProvider for the ftpClient
                Function<Path, OutputStream> outputProvider = outputStreams::get;

                // prepare reporter (empty)
                Consumer<String> progressReporter = message -> {};

                try (var ftpClient = FtpClient.getClient(server, new FTPClient())) {

                    ftpClient.downloadAllFiles(remoteBase.resolve(dataDir), outputProvider, progressReporter);

                    String downloadedFileOneContent = outputStreams.get(fileOnePath).toString();
                    String downloadedFileTwoContent = outputStreams.get(fileTwoPath).toString();

                    assertAll("Test that downloaded content is matching what was uploaded to the FTP"
                            , () -> assertLinesMatch
                                    (List.of(fileOneContent, fileTwoContent)
                                            , List.of(downloadedFileOneContent, downloadedFileTwoContent))
                    );

                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
            @Test
            @DisplayName("Then we can download content of a remote file")
            public void testDownloadSingleFile() {

                // prepare output stream
                var outputStream = new ByteArrayOutputStream();

                try (var ftpClient = FtpClient.getClient(server, new FTPClient())) {

                    ftpClient.downloadFile(remoteBase.resolve(dataDir).resolve(fileOnePath), outputStream);

                    String downloadedFileOneContent = outputStream.toString();

                    assertAll("Test that downloaded content is matching what was uploaded to the FTP"
                            , () -> assertLinesMatch
                                    (List.of(fileOneContent), List.of(downloadedFileOneContent))
                    );

                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }

        @Nested
        @DisplayName("When we login with a wrong user or password")
        class loginIsRefused {

            @Test
            @DisplayName("Then the client throws Unable to login... exception")
            public void givenNewlyCreatedFTPClient_whenBadUserName_thenException() {

                // bad username for the server
                String badUserName = "nonympus";

                Throwable exception = assertThrows(IOException.class
                        , () -> FtpClient.getClient(server, new FTPClient()
                                , fakeFtpServer.getServerControlPort(), badUserName, "")
                        , () -> "Return code 501 (Syntax error...) should cause an IOException");

                assertTrue(exception.getMessage().startsWith("Unable to login")
                        , () -> "FtpClient should throw an exception with " +
                                "'Unable to login...' message in case of a negative login reply");
            }
        }

        @Nested
        @DisplayName("When server refuses our attempt to connect")
        class connectionIsRefused {

            @BeforeEach
            public void setUp() {
                // change response to the next Connect request
                ConnectCommandHandler handler = (ConnectCommandHandler) fakeFtpServer.getCommandHandler("Connect");
                handler.setReplyCode(534);
                handler.setReplyMessageKey("Request denied for policy reasons.");
                handler.setReplyText("Request denied for policy reasons.");
            }

            @AfterEach
            public void resetServer() {
                // this is not strictly necessary
                // but will help avoid errors if we switch
                // to a single FTP server for all tests
                fakeFtpServer.setCommandHandler("Connect", new ConnectCommandHandler());
            }

            @Test
            @DisplayName("Then the client throws Unable to connect... exception")
            public void givenNewlyCreatedFTPClient_whenBadResponseOnConnect_thenException() {

                // test connect() method
                Throwable exception = assertThrows(IOException.class
                        , () -> FtpClient.getClient(server, new FTPClient()
                                , fakeFtpServer.getServerControlPort(), "anonymous", "")
                        , () -> "Return code 534 (not a positive completion) should cause an IOException");

                assertTrue(exception.getMessage().startsWith("Unable to connect")
                        , () -> "FtpClient should throw an exception with " +
                                "'Unable to connect..' message in case of a negative connect reply");
            }
        }
    }
}



















