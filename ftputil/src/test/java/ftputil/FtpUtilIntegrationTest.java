package ftputil;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class FtpUtilIntegrationTest {

    @Nested
    @DisplayName("Given no arguments provided to the main static method")
    public class GivenNoArgs {
        @Nested
        @DisplayName("When we run it")
        public class Run {
            @Test
            @DisplayName("Then a help string is printed")
            public void testMainNoArgs() {
                // redirect standard output stream
                final PrintStream standardOut = System.out;
                final ByteArrayOutputStream outputStreamCaptor = new ByteArrayOutputStream();
                System.setOut(new PrintStream(outputStreamCaptor));

                FtpUtil.main();

                String expectedStart = "usage: java -jar ftputil.jar";

                assertTrue(outputStreamCaptor.toString().startsWith("usage: java -jar ftputil.jar")
                        , () -> "Output should contain [" + expectedStart + "]");

                // restore standard output
                System.setOut(standardOut);
            }
        }
    }
}
