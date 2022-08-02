package ftputil;

import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.apache.commons.net.ftp.FTPReply;

import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

public class FtpClient implements Closeable {
    private final FTPClient ftp;
    private final URI server;

    public static FtpClient getClient(URI serverAddress, FTPClient ftp) throws IOException {
        // default port
        int port = 21;
        // username for public access data
        String user = "anonymous";
        // password for anonymous users
        String password = "";

        FtpClient client = new FtpClient(serverAddress, ftp);

        client.open(port, user, password);

        return client;
    }

    public static FtpClient getClient(URI serverAddress, FTPClient ftp
            , int port, String user, String password) throws IOException {
        FtpClient client = new FtpClient(serverAddress, ftp);

        client.open(port, user, password);

        return client;
    }

    private FtpClient(URI serverAddress, FTPClient ftp) {
        this.server = serverAddress;
        this.ftp = ftp;
    }

    private void open(int port, String user, String password) throws IOException {
        // connect
        ftp.connect(server.toString(), port);

        // check for connection failures
        if (!FTPReply.isPositiveCompletion(ftp.getReplyCode())) {
            ftp.disconnect();
            throw new IOException
                    ("Unable to connect to FTP Server: " + server
                            + " port: " + port);
        }

        // check for login failures
        if (!ftp.login(user, password)) {
            ftp.disconnect();
            throw new IOException
                    ("Unable to login to FTP Server: " + server
                            + " port: " + port);
        }

        // passive mode to be able to work from inside VMs
        ftp.enterLocalPassiveMode();

        // now ready to access files and dirs
    }

    @Override
    public void close() throws IOException {
        ftp.logout();
        ftp.disconnect();
    }

    public List<FTPFile> listFiles(Path remoteDir) throws IOException {

        FTPFile[] files = ftp.listFiles(remoteDir.toString());

        return Arrays.stream(files)
                .collect(Collectors.toList());
    }

    public void downloadFile(Path remoteFile, OutputStream out) throws IOException {
        ftp.retrieveFile(remoteFile.toString(), out);
    }

    public void downloadAllFiles
            (Path remoteDir
                    , Function<Path, OutputStream> outputProvider
                    , Consumer<String> progressReporter) throws IOException {

        var files = ftp.listFiles(remoteDir.toString());

        var filesList = Arrays.stream(files)
                .filter(FTPFile::isFile)
                .map(FTPFile::getName)
                .collect(Collectors.toList());

        for(int i = 0; i < filesList.size(); i++) {
            progressReporter.accept(String.format
                    ("Downloading (%d of %d):[%s]", i+1, filesList.size(), filesList.get(i)));

            try (var out = outputProvider.apply(Path.of(filesList.get(i)))) {
                ftp.retrieveFile(remoteDir.resolve(filesList.get(i)).toString(), out);
            }
        }
    }

}
