package br.com.fiap.workshop_management_system.identity;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * Reads the request body once and replays it on every {@link #getInputStream()}/{@link #getReader()} call.
 *
 * <p>Needed because the HMAC filter must hash the raw body while the JSON message converter still has to
 * deserialize it later in the chain. {@code ContentCachingRequestWrapper} does not fit: it only exposes the
 * bytes already consumed through it and never replays them to a second reader.
 *
 * <p>The read is capped because it happens before the caller is authenticated: without a limit an anonymous
 * caller could make the server hold an arbitrarily large body in memory.
 */
class CachedBodyHttpServletRequest extends HttpServletRequestWrapper {

    private final byte[] body;

    CachedBodyHttpServletRequest(HttpServletRequest request, int maxBodyBytes) throws IOException {
        super(request);
        byte[] read = request.getInputStream().readNBytes(maxBodyBytes + 1);
        if (read.length > maxBodyBytes) {
            throw new BodyTooLargeException(maxBodyBytes);
        }
        this.body = read;
    }

    byte[] body() {
        return body.clone();
    }

    @Override
    public ServletInputStream getInputStream() {
        return new CachedBodyServletInputStream(body);
    }

    @Override
    public BufferedReader getReader() {
        return new BufferedReader(new InputStreamReader(new ByteArrayInputStream(body), charset()));
    }

    private Charset charset() {
        String encoding = getCharacterEncoding();
        // JSON bodies default to UTF-8 when the client sends no charset.
        return encoding == null ? StandardCharsets.UTF_8 : Charset.forName(encoding);
    }

    static final class BodyTooLargeException extends IOException {

        BodyTooLargeException(int maxBodyBytes) {
            super("Request body exceeds " + maxBodyBytes + " bytes");
        }
    }

    private static final class CachedBodyServletInputStream extends ServletInputStream {

        private final ByteArrayInputStream delegate;

        private CachedBodyServletInputStream(byte[] body) {
            this.delegate = new ByteArrayInputStream(body);
        }

        @Override
        public int read() {
            return delegate.read();
        }

        @Override
        public int read(byte[] buffer, int offset, int length) {
            return delegate.read(buffer, offset, length);
        }

        @Override
        public boolean isFinished() {
            return delegate.available() == 0;
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setReadListener(ReadListener readListener) {
            throw new UnsupportedOperationException("Async reads are not supported for a cached body");
        }
    }
}
