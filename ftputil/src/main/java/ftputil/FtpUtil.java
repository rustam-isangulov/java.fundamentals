package ftputil;

import org.apache.commons.cli.ParseException;
import org.apache.commons.net.ftp.FTPClient;

import java.io.*;
import java.net.URI;
import java.util.function.Consumer;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.function.Function;


public class FtpUtil {
    public static void main(String... args) {

        var cli = new CliParser();

        try {
            // understand user defined options
            cli.parse(args);

            // ready to go
            System.out.println();
            System.out.println("Proceeding with the following parameters");
            cli.printReport();

            // prepare necessary directory structure
            Files.createDirectories(cli.getLocalBase().resolve(cli.getDir()));

            /// create an instance
            FtpUtil utility = new FtpUtil(cli.getServer()
            , cli.getRemoteBase(), cli.getDir(), cli.getLocalBase());

            // divider from previous outputs
            System.out.println();

            // run the job
            utility.ConnectAndDownload();

        } catch (ParseException ex) {
            System.out.println("Parsing of command line arguments failed: "
                    + ex.getMessage());
            System.out.println();
            cli.printHelp();

        } catch (IOException ex) {
            System.out.format("Unable to create local directory: [%s] reason: [%s]"
                    , cli.getLocalBase().resolve(cli.getDir()), ex.getMessage());
            System.out.println();
        } catch (RuntimeException ex) {
            System.out.println("Error: " + ex.getMessage());

            if (ex.getCause() != null) {
                System.out.println("Reason: " + ex.getCause().getMessage());
            }
        }
    }

    private final URI server;
    private final Path fullRemotePath;
    private final Path fullLocalPath;

    public FtpUtil(URI server, Path remoteBase, Path dataDir, Path localBase) {
        this.server = server;
        this.fullRemotePath = remoteBase.resolve(dataDir);
        this.fullLocalPath = localBase.resolve(dataDir);
    }

    public void ConnectAndDownload () {
        // prepare to measure elapsed time
        long startTime = System.nanoTime();

        // download files from FTP server
        try(var client = FtpClient.getClient(server, new FTPClient())) {

            // provide output stream to copy a remote file content into
            Function<Path, OutputStream> outputProvider =
                    file -> {
                        try {
                            return new BufferedOutputStream(
                                    new FileOutputStream
                                            (fullLocalPath.resolve(file).toFile()));
                        } catch (FileNotFoundException e) {
                            throw new RuntimeException("Cannot create files in the local directory...",e);
                        }
                    };

            // react to FtpClient updates
            Consumer<String> downloadProgressEvent = System.out::println;

            // download all files form the remoteDir
            client.downloadAllFiles(fullRemotePath, outputProvider, downloadProgressEvent);

        } catch (IOException ex) {
            System.out.println("Communication with FTP server failed...");
            ex.printStackTrace();
            return;
        }

        // report elapsed time
        long elapsedTime = System.nanoTime() - startTime;

        System.out.println();
        System.out.format("elapsed time: %.0f (ms)",elapsedTime * 1e-6);
        System.out.println();
    }

    public URI getServer() {
        return server;
    }

    public Path getFullRemotePath() {
        return fullRemotePath;
    }

    public Path getFullLocalPath() {
        return fullLocalPath;
    }
}
