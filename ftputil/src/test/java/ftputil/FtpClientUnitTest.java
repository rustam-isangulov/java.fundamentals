package ftputil;

import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InOrder;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.verify;

public class FtpClientUnitTest {

    @Nested
    @DisplayName("Test successful ftp connection")
    public class openIfSuccessful {
        private final FTPClient mockFTPClient = mock(FTPClient.class);

        private FtpClient client;

        private FTPFile[] ftpFiles;

        @BeforeEach
        public void setUp() throws IOException {
            // set up a happy path for connecting / login sequence
            when(mockFTPClient.getReplyCode())
                    .thenReturn(230);
            when(mockFTPClient.login(anyString(), anyString()))
                    .thenReturn(true);

            client = FtpClient.getClient
                    (URI.create("localhost"), mockFTPClient);

            // setup file content for the mock FTPClient
            ftpFiles = new FTPFile[2];
            ftpFiles[0] = new FTPFile();
            ftpFiles[0].setName("fileOne.txt");
            ftpFiles[0].setType(FTPFile.FILE_TYPE);
            ftpFiles[1] = new FTPFile();
            ftpFiles[1].setName("fileTwo.txt");
            ftpFiles[1].setType(FTPFile.FILE_TYPE);

            // mock FTPClient response for the list command
            when(mockFTPClient.listFiles(anyString()))
                    .thenReturn(ftpFiles);

        }

        @Test
        @DisplayName("Test open connection sequence for default port/user")
        public void testFTPOpenDefault() throws IOException {
            // open connection should have three steps:
            // connect to server @ port
            verify(mockFTPClient).connect("localhost", 21);
            // log in with username anonymous and no password
            verify(mockFTPClient).login("anonymous", "");
            // switch to passive mode to be able to work from VMs
            // (behind NATs and port mapping)
            verify(mockFTPClient).enterLocalPassiveMode();
        }

        @Test
        @DisplayName("Test close sequence")
        public void testFTPClose() throws IOException {
            // close (as in Closeable interface)
            client.close();

            // order of calls verification
            InOrder inOrder = inOrder(mockFTPClient);

            inOrder.verify(mockFTPClient).logout();
            inOrder.verify(mockFTPClient).disconnect();
        }

        @Test
        @DisplayName("Test list files call")
        public void testFTPListFiles() throws IOException {

            // mock FTPClient response for the list command
            when(mockFTPClient.listFiles(anyString()))
                    .thenReturn(ftpFiles);

            // get the list through FtpClient
            List<FTPFile> list = client.listFiles(Path.of("/any/path"));

            assertLinesMatch(List.of("fileOne.txt", "fileTwo.txt")
                    , list.stream().map(FTPFile::getName).collect(Collectors.toList()));
        }

        @Test
        @DisplayName("Test downloadAllFiles call")
        public void testFTPDownloadAllFiles() throws IOException {

            // create a simple progress reporter
            Consumer<String> progressReporter = mock(Consumer.class);
            // create order verifier for the progress reporter mock
            InOrder inOrderReporter = inOrder(progressReporter);

            // create a simple output stream
            OutputStream out = mock(OutputStream.class);
            // create a simple output provider
            Function<Path, OutputStream> outputProvider = mock(Function.class);
            when(outputProvider.apply(any(Path.class))).thenReturn(out);
            // create order verifier for the output provider mock
            InOrder inOrderProvider = inOrder(outputProvider);

            client.downloadAllFiles
                    (Path.of("/remote/path"), outputProvider, progressReporter);

            // check the download sequence
            // get a list
            verify(mockFTPClient).listFiles("/remote/path");

            // say "I am downloading ..." twice
            inOrderReporter.verify(progressReporter).accept("Downloading (1 of 2):[fileOne.txt]");
            inOrderReporter.verify(progressReporter).accept("Downloading (2 of 2):[fileTwo.txt]");

            // get output stream to write to... twice
            inOrderProvider.verify(outputProvider).apply(Path.of("fileOne.txt"));
            inOrderProvider.verify(outputProvider).apply(Path.of("fileTwo.txt"));

            // check if retrieveFile method has been called
            verify(mockFTPClient).retrieveFile("/remote/path/fileOne.txt", out);
            verify(mockFTPClient).retrieveFile("/remote/path/fileTwo.txt", out);
        }

        @Test
        @DisplayName("Test downloading a file call")
        public void testFTPDownloadFile(@TempDir Path dataDir) throws IOException {
            // prepare an output stream to copy file content
            var out = new FileOutputStream
                    (dataDir.resolve("test.txt").toFile());

            // try to download a file with the given path
            client.downloadFile(Path.of("any/path"), out);

            verify(mockFTPClient).retrieveFile("any/path", out);
        }
    }

    @Nested
    @DisplayName("Test unsuccessful open connection cases")
    public class unsuccessfulCases {
        private FTPClient mockFTPClient = mock(FTPClient.class);

        @Test
        @DisplayName("Test connect problem behaviour")
        public void testConnectProblem() throws IOException {
            // set up a troublesome path for the connecting sequence
            when(mockFTPClient.getReplyCode())
                    .thenReturn(534);

            Throwable ex = assertThrows(IOException.class
                    , () -> FtpClient.getClient(URI.create("localhost"), mockFTPClient)
                    , () -> "FtpClient should throw an exception when ftp return code is not positive");
        }

        @Test
        @DisplayName("Test login problem behaviour")
        public void testLoginProblem() throws IOException {
            // set up a happy path for the connecting sequence
            when(mockFTPClient.getReplyCode())
                    .thenReturn(230);

            // but bad response for the login call
            when(mockFTPClient.login(anyString(), anyString()))
                    .thenReturn(false);

            Throwable ex = assertThrows(IOException.class
                    , () -> FtpClient.getClient(URI.create("localhost"), mockFTPClient)
                    , () -> "FtpClient should throw an exception when ftp login is not successful");
        }
    }

    @Nested
    @DisplayName("When connecting with non-default port and user")
    public class connectDetailed {
        private final FTPClient mockFTPClient = mock(FTPClient.class);

        @Test
        @DisplayName("Test open connection sequence with port / user / password")
        public void testFTPOpenDetailed() throws IOException {
            // set up a happy path for connecting / login sequence
            when(mockFTPClient.getReplyCode())
                    .thenReturn(230);
            when(mockFTPClient.login(any(), any()))
                    .thenReturn(true);

            FtpClient client = FtpClient.getClient
                    (URI.create("ftp.server"), mockFTPClient
                            , 22, "userOne", "passwordSecret");


            // open connection should have three steps:
            // connect to server @ port
            verify(mockFTPClient).connect("ftp.server", 22);
            // log in with username anonymous and no password
            verify(mockFTPClient).login("userOne", "passwordSecret");
            // switch to passive mode to be able to work from VMs
            // (behind NATs and port mapping)
            verify(mockFTPClient).enterLocalPassiveMode();
        }
    }
}
