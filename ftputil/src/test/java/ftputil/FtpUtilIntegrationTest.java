package ftputil;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.mockftpserver.core.command.ConnectCommandHandler;
import org.mockftpserver.fake.FakeFtpServer;
import org.mockftpserver.fake.UserAccount;
import org.mockftpserver.fake.filesystem.DirectoryEntry;
import org.mockftpserver.fake.filesystem.FileEntry;
import org.mockftpserver.fake.filesystem.FileSystem;
import org.mockftpserver.fake.filesystem.UnixFakeFileSystem;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class FtpUtilIntegrationTest {

    private final Path fileOnePath = Path.of("file_one.json");
    private final Path fileTwoPath = Path.of("file_two_json");

    private final String fileOneContent = "{\"id\":\"1\",\"approvedSymbol\":\"AAA\"}";
    private final String fileTwoContent = "{\"id\":\"2\",\"approvedSymbol\":\"BBB\"}";

    @Nested
    @DisplayName("Given no arguments provided to the main static method")
    public class GivenNoArgs {

        @Nested
        @DisplayName("When we run it")
        public class Run {
            @Test
            @DisplayName("Then an error and help string are printed")
            public void testMainNoArgs() {
                // redirect standard output stream
                final PrintStream standardOut = System.out;
                final ByteArrayOutputStream outputStreamCaptor = new ByteArrayOutputStream();
                System.setOut(new PrintStream(outputStreamCaptor));

                FtpUtil.main();

                String expectedStart = "usage: java -jar ftputil.jar";

                assertTrue(outputStreamCaptor.toString().startsWith
                                ("Parsing of command line arguments failed: " +
                                        "Missing required options: s, r, l, d")
                        , () -> "Output should contain [" + expectedStart + "]");

                // restore standard output
                System.setOut(standardOut);
            }
        }
    }

    @Nested
    @DisplayName("Given arguments are provided to the main static method")
    public class GivenArgs {

        @Nested
        @DisplayName("When an unsupported option is provided")
        public class UnsupportedOption {

            @Test
            @DisplayName("Then the utility quits with 'Parsing...' error")
            public void testBadOption() {
                String[] badString = new String[]
                        {"-s", "server"
                                , "-badargument", "remote"
                                , "-l", "local"
                                , "-d", "directory_name"};

                // redirect standard output stream
                final PrintStream standardOut = System.out;
                final ByteArrayOutputStream outputStreamCaptor = new ByteArrayOutputStream();
                System.setOut(new PrintStream(outputStreamCaptor));

                FtpUtil.main(badString);

                assertTrue(outputStreamCaptor.toString()
                        .contains("Parsing of command line arguments failed: Unrecognized option: -badargument"));

                // restore standard output
                System.setOut(standardOut);
            }
        }

        @Nested
        @DisplayName("When an inaccessible local directory is provided")
        public class InaccessibleLocalDirOption {

            @Test
            @DisplayName("Then the utility quits with 'Unable to create...' error")
            public void testBadOption() {
                String[] badString = new String[]
                        {"-s", "server"
                                , "-r", "remote"
                                , "-l", "/null"
                                , "-d", "directory_name"};

                // redirect standard output stream
                final PrintStream standardOut = System.out;
                final ByteArrayOutputStream outputStreamCaptor = new ByteArrayOutputStream();
                System.setOut(new PrintStream(outputStreamCaptor));

                FtpUtil.main(badString);

                assertTrue(outputStreamCaptor.toString()
                        .contains("Unable to create local directory: [/null/directory_name] " +
                                "reason: [/null: Read-only file system]"));

                // restore standard output
                System.setOut(standardOut);
            }
        }
    }

    @Nested
    @DisplayName("Given correct server uri, directories and accessible ftp server")
    public class testCorrectSetup {
        @TempDir
        public Path localBase;

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
        @DisplayName("When we run the main method with correct options")
        public class testMain {

            @Test
            @DisplayName("Then we can download content remote files")
            public void testMainHappyPath() throws IOException {
                String[] argString = new String[]
                        {"-s", server.toString()
                                , "-r", remoteBase.toString()
                                , "-l", localBase.toString()
                                , "-d", dataDir.toString()};

                // run the main method
                FtpUtil.main(argString);

                // create the local file path given pre-defined remote file name
                Path downloadedFileOne = localBase.resolve(dataDir).resolve(fileOnePath);
                Path downloadedFileTwo = localBase.resolve(dataDir).resolve(fileTwoPath);

                long filesCount;
                String fileContentOne;
                String fileContentTwo;

                try (var listStream = Files.list(localBase.resolve(dataDir));
                     var readerOne = Files.newBufferedReader(downloadedFileOne);
                     var readerTwo = Files.newBufferedReader(downloadedFileTwo)) {

                    filesCount = listStream.count();
                    fileContentOne = readerOne.readLine();
                    fileContentTwo = readerTwo.readLine();
                }

                // assert both number of files and content
                assertAll(
                        () -> assertEquals(2, filesCount)
                        , () -> assertEquals(fileContentOne, fileOneContent)
                        , () -> assertEquals(fileContentTwo, fileTwoContent)
                );
            }
        }

        @Nested
        @DisplayName("When we run the main method with local directory that is not writable")
        public class testMainDirReadOnly {

            @Test
            @DisplayName("Then we can it quits with 'Cannot create...' error")
            public void testMainHappyPath() throws IOException {
                String[] argString = new String[]
                        {"-s", server.toString()
                                , "-r", remoteBase.toString()
                                , "-l", localBase.toString()
                                , "-d", dataDir.toString()};

                // make the local directory read-only
                Set<PosixFilePermission> perms =
                        PosixFilePermissions.fromString("r--r--r--");
                FileAttribute<Set<PosixFilePermission>> attr =
                        PosixFilePermissions.asFileAttribute(perms);
                // prepare necessary directory structure
                Files.createDirectories(localBase.resolve(dataDir), attr);


                // redirect standard output stream
                final PrintStream standardOut = System.out;
                final ByteArrayOutputStream outputStreamCaptor = new ByteArrayOutputStream();
                System.setOut(new PrintStream(outputStreamCaptor));

                // run the main method
                FtpUtil.main(argString);

                assertTrue(outputStreamCaptor.toString()
                        .contains("Cannot create files in the local directory..."));

                // restore standard output
                System.setOut(standardOut);
            }
        }

        @Nested
        @DisplayName("When we create an instance of FtpUtil")
        public class testInstance {

            private FtpUtil ftpUtil;

            @BeforeEach
            public void setUp() {
                // setup FtpUtil
                ftpUtil = new FtpUtil(server, remoteBase, dataDir, localBase);
            }

            @Test
            @DisplayName("Then the instance has correct full remote and local paths")
            public void testCorrectPaths() {

                assertAll(
                        () -> assertEquals(remoteBase.resolve(dataDir), ftpUtil.getFullRemotePath())
                        , () -> assertEquals(localBase.resolve(dataDir), ftpUtil.getFullLocalPath())
                        , () -> assertEquals(server, ftpUtil.getServer())
                );
            }

            @Test
            @DisplayName("Then we can download content of remote files")
            public void testCorrectDownload() throws IOException {
                // prepare necessary directory structure
                Files.createDirectories(ftpUtil.getFullLocalPath());

                // run the download
                ftpUtil.ConnectAndDownload();

                // create the local file path given pre-defined remote file name
                Path downloadedFileOne = ftpUtil.getFullLocalPath().resolve(fileOnePath);
                Path downloadedFileTwo = ftpUtil.getFullLocalPath().resolve(fileTwoPath);

                long filesCount;
                String fileContentOne;
                String fileContentTwo;

                try (var listStream = Files.list(ftpUtil.getFullLocalPath());
                     var readerOne = Files.newBufferedReader(downloadedFileOne);
                     var readerTwo = Files.newBufferedReader(downloadedFileTwo)) {

                    filesCount = listStream.count();
                    fileContentOne = readerOne.readLine();
                    fileContentTwo = readerTwo.readLine();
                }

                // assert both number of files and content
                assertAll(
                        () -> assertEquals(2, filesCount)
                        , () -> assertEquals(fileContentOne, fileOneContent)
                        , () -> assertEquals(fileContentTwo, fileTwoContent)
                );
            }

            @Test
            @DisplayName("Then it quits if the ftp server communications fails")
            public void testFTPError() throws IOException {
                // change response to the next Connect request
                ConnectCommandHandler handler = (ConnectCommandHandler) fakeFtpServer.getCommandHandler("Connect");
                handler.setReplyCode(534);
                handler.setReplyMessageKey("Request denied for policy reasons.");
                handler.setReplyText("Request denied for policy reasons.");

                // prepare necessary directory structure
                Files.createDirectories(ftpUtil.getFullLocalPath());

                // redirect standard output stream
                final PrintStream standardOut = System.out;
                final ByteArrayOutputStream outputStreamCaptor = new ByteArrayOutputStream();
                System.setOut(new PrintStream(outputStreamCaptor));


                // run the download method
                ftpUtil.ConnectAndDownload();

                assertTrue(outputStreamCaptor.toString()
                        .contains("Communication with FTP server failed..."));


                // restore standard output
                System.setOut(standardOut);

                // back to normal response
                fakeFtpServer.setCommandHandler("Connect", new ConnectCommandHandler());
            }

            @Test
            @DisplayName("Then it quits when FileNotFoundException occurs")
            public void testDownloadWithIOProblem() throws IOException {

                // prepare necessary directory structure
                //Files.createDirectories(ftpUtil.getFullLocalPath());

                // run the download
               Throwable ex = assertThrows(RuntimeException.class, () -> ftpUtil.ConnectAndDownload());

               assertAll("Test exceptions thrown when no local directory is available"
                       , () -> assertLinesMatch
                               (List.of("Cannot create files in the local directory...")
                                       , List.of(ex.getMessage()))
                       , () -> assertEquals(FileNotFoundException.class, ex.getCause().getClass())
               );
            }
        }
    }
}














