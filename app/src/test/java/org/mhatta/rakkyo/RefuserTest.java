package org.mhatta.rakkyo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;

import org.junit.Test;

/** The paused state: connections are closed at once, and the port stays held. */
public class RefuserTest {
    private static int read(Socket s) {
        try {
            s.setSoTimeout(5000);
            final InputStream in = s.getInputStream();
            return in.read();
        } catch (IOException e) {
            // A reset is a refusal too.
            return -1;
        }
    }

    @Test
    public void refusesAndKeepsThePort() throws Exception {
        try (ServerSocket server = new ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))) {
            final TorController.Refuser r = new TorController.Refuser(server);
            r.start();
            for (int i = 0; i < 3; i++) {
                try (Socket client = new Socket("127.0.0.1", server.getLocalPort())) {
                    try {
                        client.getOutputStream().write(new byte[] { 5, 1, 0 });
                    } catch (IOException e) {
                        // Already reset: refused before the greeting went out.
                    }
                    assertEquals(-1, read(client));
                }
            }
            r.finish();
            assertFalse(r.isAlive());
            assertFalse(server.isClosed());
            assertEquals(0, server.getSoTimeout());

            // Taken over: the next owner accepts on the same socket.
            try (Socket client = new Socket("127.0.0.1", server.getLocalPort())) {
                server.setSoTimeout(5000);
                try (Socket accepted = server.accept()) {
                    accepted.getOutputStream().write(42);
                    assertEquals(42, read(client));
                }
            }
        }
    }

    @Test
    public void endsWhenThePortCloses() throws Exception {
        final ServerSocket server = new ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"));
        final TorController.Refuser r = new TorController.Refuser(server);
        r.start();
        server.close();
        r.join(2000);
        assertFalse(r.isAlive());
        r.finish();
    }
}
