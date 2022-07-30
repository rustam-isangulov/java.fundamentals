package ftputil;

import org.apache.commons.cli.*;

import java.net.URI;
import java.nio.file.Path;

public class CliParser {
    private URI server;
    private Path remoteBase;
    private Path localBase;
    private Path dir;

    private final Option serverURI = Option.builder()
            .option("s")
            .longOpt("server")
            .argName("ftp_address")
            .required()
            .hasArg()
            .desc("remote ftp server uri")
            .build();

    private final Option remoteBaseOp = Option.builder()
            .option("r")
            .longOpt("remotedir")
            .argName("remote_dir")
            .required()
            .hasArg()
            .desc("remote base directory")
            .build();

    private final Option localBaseOp = Option.builder()
            .option("l")
            .longOpt("localdir")
            .argName("local_dir")
            .required()
            .hasArg()
            .desc("local base directory")
            .build();

    private final Option dirOp = Option.builder()
            .option("d")
            .longOpt("dir")
            .argName("dir")
            .required()
            .hasArg()
            .desc("directory to download files from (relative to remotedir) and to (relative to localdir)")
            .build();

    // define options

    private final Options options = new Options();

    {
        options.addOption(serverURI);
        options.addOption(remoteBaseOp);
        options.addOption(localBaseOp);
        options.addOption(dirOp);
    }

    public URI getServer() {
        return server;
    }

    public Path getRemoteBase() {
        return remoteBase;
    }

    public Path getLocalBase() {
        return localBase;
    }

    public Path getDir() {
        return dir;
    }

    public void parse(String... args) throws ParseException {
        // parse the command line

        CommandLineParser parser = new DefaultParser();
        CommandLine line = parser.parse(options, args);

        // parse server
        server = URI.create(line.getOptionValue(serverURI));

        // parse base directory
        remoteBase = Path.of(line.getOptionValue(remoteBaseOp));

        // parse base directory
        localBase = Path.of(line.getOptionValue(localBaseOp));

        // parse target directory
        dir = Path.of(line.getOptionValue(dirOp));

        //throw new ParseException("test");
    }

    public void printReport() {
        System.out.println("\tServer: [" + this.getServer() + "]");
        System.out.println("\tRemote: [" + this.getRemoteBase() + "]");
        System.out.println("\tLocal:  [" + this.getLocalBase() + "]");
        System.out.println("\tDir:    [" + this.getDir() + "]");
    }

    public void printHelp() {
        HelpFormatter formatter = new HelpFormatter();
        String footer = "\nExample:\n java -jar ftputil.jar" +
                " -s \"ftp.ebi.ac.uk\"" +
                " -r \"/pub/databases/opentargets/platform/latest/output/etl/json/\"" +
                " -l \"./data/\"" +
                " -d \"diseases\"";
        formatter.printHelp
                ("java -jar ftputil.jar"
                        , "\nDownload files from a directory on an ftp server"
                                + "\n\nOptions:" , options, footer, true);
    }
}
