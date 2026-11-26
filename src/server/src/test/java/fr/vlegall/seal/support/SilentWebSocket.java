package fr.vlegall.seal.support;

import jakarta.servlet.http.Cookie;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Client WebSocket brut qui ne lit jamais (TRN-13, A10) : la contre-pression TCP remplit les tampons du serveur, comme
 * un client lent ou malveillant.
 */
public final class SilentWebSocket implements AutoCloseable {
    private final Socket socket = new Socket();
    private final OutputStream out;
    private final InputStream in;
    private final SecureRandom random = new SecureRandom();

    public SilentWebSocket(int port, Cookie session) throws Exception {
        socket.setReceiveBufferSize(1024);
        socket.connect(new InetSocketAddress("localhost", port), 5000);
        out = socket.getOutputStream();
        in = socket.getInputStream();
        byte[] key = new byte[16];
        random.nextBytes(key);
        String request = "GET /ws HTTP/1.1\r\nHost: localhost:" + port + "\r\nUpgrade: websocket\r\n"
                + "Connection: Upgrade\r\nSec-WebSocket-Version: 13\r\nSec-WebSocket-Key: "
                + Base64.getEncoder().encodeToString(key) + "\r\nOrigin: " + TestWebSocketClient.ORIGIN
                + "\r\nCookie: " + session.getName() + "=" + session.getValue() + "\r\n\r\n";
        out.write(request.getBytes(StandardCharsets.US_ASCII));
        out.flush();
        ByteArrayOutputStream head = new ByteArrayOutputStream();
        int last4 = 0;
        while (true) {
            int b = in.read();
            if (b < 0) {
                throw new IllegalStateException("fermé pendant la poignée de main");
            }
            head.write(b);
            last4 = (last4 << 8) | b;
            if (last4 == 0x0D0A0D0A) {
                break;
            }
        }
        String status =
                head.toString(StandardCharsets.US_ASCII).lines().findFirst().orElse("");
        if (!status.contains("101")) {
            throw new IllegalStateException(status);
        }
    }

    /** Envoie une trame texte masquée, sans jamais rien lire. */
    public void sendText(String text) throws Exception {
        byte[] payload = text.getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream frame = new ByteArrayOutputStream();
        frame.write(0x81);
        if (payload.length < 126) {
            frame.write(0x80 | payload.length);
        } else {
            frame.write(0x80 | 126);
            frame.write(payload.length >> 8);
            frame.write(payload.length & 0xFF);
        }
        byte[] mask = new byte[4];
        random.nextBytes(mask);
        frame.write(mask);
        for (int i = 0; i < payload.length; i++) {
            frame.write(payload[i] ^ mask[i & 3]);
        }
        out.write(frame.toByteArray());
    }

    @Override
    public void close() throws IOException {
        socket.close();
    }
}
