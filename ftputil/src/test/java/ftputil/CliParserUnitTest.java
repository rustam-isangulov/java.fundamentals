package ftputil;

import org.apache.commons.cli.ParseException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.URI;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class CliParserUnitTest {

    private CliParser cli = new CliParser();

    @Test
    @DisplayName("Test successful parsing")
    public void testSuccessfulParsing() {
        String[] argString = new String[]
                { "-s", "server"
                        , "-r", "remote"
                        , "-l", "local"
                        , "-d", "directory_name"};

        try {
            cli.parse(argString);
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }

        assertAll(
                () -> assertEquals(cli.getServer(), URI.create("server"))
                , () -> assertEquals(cli.getRemoteBase(), Path.of("remote"))
                , () -> assertEquals(cli.getLocalBase(), Path.of("local"))
                , () -> assertEquals(cli.getDir(), Path.of("directory_name")));
    }

    @Test
    @DisplayName("Test a bad option parsing")
    public void testBadOptionParsing() {
        String[] argString = new String[]
                { "-s", "server"
                        , "-badoption", "remote"
                        , "-l", "local"
                        , "-d", "directory_name"};

        assertThrows(ParseException.class, () -> cli.parse(argString));
    }

    @Test
    @DisplayName("Test no arguments parsing")
    public void testNoArgsParsing() {
        String[] argString = new String[]{};

        assertThrows(ParseException.class, () -> cli.parse(argString));
    }

    @Test
    @DisplayName("Test help string")
    public void testHelpString() {
        // redirect standard output stream
        final PrintStream standardOut = System.out;
        final ByteArrayOutputStream outputStreamCaptor = new ByteArrayOutputStream();
        System.setOut(new PrintStream(outputStreamCaptor));

        cli.printHelp();

        assertTrue(outputStreamCaptor.toString().startsWith("usage: java -jar ftputil.jar"));

        // restore standard output
        System.setOut(standardOut);
    }

    @Test
    @DisplayName("Test printing report")
    public void testReport() {
        // redirect standard output stream
        final PrintStream standardOut = System.out;
        final ByteArrayOutputStream outputStreamCaptor = new ByteArrayOutputStream();
        System.setOut(new PrintStream(outputStreamCaptor));

        cli.printReport();

        assertTrue(outputStreamCaptor.toString()
                .startsWith("\tServer: [" + cli.getServer() + "]"));

        // restore standard output
        System.setOut(standardOut);
    }
}








