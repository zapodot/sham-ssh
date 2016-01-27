package software.sham.ssh;

import static org.junit.Assert.*;
import static org.hamcrest.Matchers.*;

import com.jcraft.jsch.ChannelShell;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.ByteBuffer;
import java.util.Properties;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

public class FunctionalTest {
    private final Logger logger = LoggerFactory.getLogger(getClass());
    MockSshServer server;
    Session sshSession;
    ChannelShell sshChannel;
    ByteArrayOutputStream sshClientOutput;
    PrintWriter inputWriter;

    @Before
    public void initSsh() throws IOException {
        server = new MockSshServer(9022);
    }

    @Before
    public void initSshClient() throws JSchException, IOException {
        JSch jsch = new JSch();
        sshSession = jsch.getSession("tester", "localhost", 9022);
        Properties config = new Properties();
        config.setProperty("StrictHostKeyChecking", "no");
        sshSession.setConfig(config);
        sshSession.setPassword("testing");
        sshSession.connect();
        sshChannel = (ChannelShell) sshSession.openChannel("shell");
        PipedInputStream channelIn = new PipedInputStream();
        sshChannel.setInputStream(channelIn);
        OutputStream sshClientInput = new PipedOutputStream(channelIn);
        inputWriter = new PrintWriter(sshClientInput);
        sshClientOutput = new ByteArrayOutputStream();
        sshChannel.setOutputStream(sshClientOutput);
        sshChannel.connect(1000);
    }

    @After
    public void stopSsh() throws IOException {
        sshSession.disconnect();
        server.stop();
    }

    @Test
    public void defaultShellCommandsShouldSilentlySucceed() throws Exception {
        sendTextToServer("Knock knock\n");
        assertThat(sshClientOutput.size(), equalTo(0));
    }

    @Test
    public void defaultShellShouldDisconnectOnExit() throws Exception {
        sendTextToServer("exit\n");
        Thread.sleep(300);
        assertThat(sshChannel.isConnected(), is(false));
    }

    @Test
    public void singleOutput() throws Exception {
        server.respondTo(any(String.class))
            .withOutput("hodor\n");

        sendTextToServer("Knock knock\n");
        assertEquals("hodor\n", sshClientOutput.toString());
    }

    @Test
    public void multipleOutput() throws Exception {
        server.respondTo(any(String.class))
                .withOutput("Starting...\n")
                .withOutput("Completed.\n");

        sendTextToServer("start");
        assertEquals("Starting...\nCompleted.\n", sshClientOutput.toString());
    }

    @Test
    public void delayedOutput() throws Exception {
        server.respondTo(any(String.class))
                .withOutput("Starting...\n")
                .withDelay(500)
                .withOutput("Completed.\n");

        sendTextToServer("start");
        logger.debug("Checking for first line");
        assertEquals("Starting...\n", sshClientOutput.toString());
        Thread.sleep(500);
        logger.debug("Checking for second line");
        assertEquals("Starting...\nCompleted.\n", sshClientOutput.toString());
    }

    @Test
    public void differentOutputForDifferentInput() throws Exception {
        server.respondTo(any(String.class))
                .withOutput("default\n");
        server.respondTo("Knock knock")
                .withOutput("Who's there?\n");

        sendTextToServer("Something wicked this way comes");
        String output = sshClientOutput.toString();
        assertEquals("default\n", output);
        sendTextToServer("Knock knock");
        assertEquals("default\nWho's there?\n", sshClientOutput.toString());
    }

    private void sendTextToServer(final String text) throws Exception {
        inputWriter.write(text);
        inputWriter.flush();
        logger.debug("Sent text to SSH server: {}", text);
        Thread.sleep(100);
    }
}
