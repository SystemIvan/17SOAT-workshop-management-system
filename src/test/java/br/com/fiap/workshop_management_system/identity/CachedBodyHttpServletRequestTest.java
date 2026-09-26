package br.com.fiap.workshop_management_system.identity;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CachedBodyHttpServletRequestTest {

    private static final int LIMIT = 1024;
    private static final String BODY = "{\"decisions\":[{\"serviceExecutionId\":\"abc\",\"decision\":\"APPROVED\"}]}";

    @Test
    void getInputStreamReturnsTheFullBodyOnEveryCall() throws IOException {
        CachedBodyHttpServletRequest request = new CachedBodyHttpServletRequest(requestWithBody(BODY), LIMIT);

        byte[] first = request.getInputStream().readAllBytes();
        byte[] second = request.getInputStream().readAllBytes();

        assertArrayEquals(BODY.getBytes(StandardCharsets.UTF_8), first);
        assertArrayEquals(first, second);
    }

    @Test
    void getReaderAfterGetInputStreamStillReturnsTheFullBody() throws IOException {
        CachedBodyHttpServletRequest request = new CachedBodyHttpServletRequest(requestWithBody(BODY), LIMIT);

        request.getInputStream().readAllBytes();

        assertEquals(BODY, readAll(request.getReader()));
    }

    @Test
    void readerHonorsTheRequestCharacterEncoding() throws IOException {
        String body = "{\"note\":\"orçamento aprovado\"}";
        MockHttpServletRequest original = new MockHttpServletRequest();
        original.setCharacterEncoding(StandardCharsets.ISO_8859_1.name());
        original.setContent(body.getBytes(StandardCharsets.ISO_8859_1));

        CachedBodyHttpServletRequest request = new CachedBodyHttpServletRequest(original, LIMIT);

        assertEquals(body, readAll(request.getReader()));
    }

    @Test
    void emptyBodyIsReadWithoutFailing() throws IOException {
        MockHttpServletRequest empty = new MockHttpServletRequest();
        CachedBodyHttpServletRequest request = new CachedBodyHttpServletRequest(empty, LIMIT);

        assertEquals(0, request.getInputStream().readAllBytes().length);
        assertEquals("", readAll(request.getReader()));
        assertTrue(request.getInputStream().isFinished());
    }

    @Test
    void bodyReturnsADefensiveCopy() throws IOException {
        CachedBodyHttpServletRequest request = new CachedBodyHttpServletRequest(requestWithBody(BODY), LIMIT);

        request.body()[0] = 'X';

        assertArrayEquals(BODY.getBytes(StandardCharsets.UTF_8), request.body());
    }

    @Test
    void bodyExactlyAtTheLimitIsAccepted() throws IOException {
        String body = "x".repeat(LIMIT);

        CachedBodyHttpServletRequest request = new CachedBodyHttpServletRequest(requestWithBody(body), LIMIT);

        assertEquals(LIMIT, request.body().length);
    }

    @Test
    void bodyOverTheLimitIsRejectedWithoutBufferingIt() {
        MockHttpServletRequest original = requestWithBody("x".repeat(LIMIT + 1));

        assertThrows(CachedBodyHttpServletRequest.BodyTooLargeException.class,
                () -> new CachedBodyHttpServletRequest(original, LIMIT));
    }

    private static MockHttpServletRequest requestWithBody(String body) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setContent(body.getBytes(StandardCharsets.UTF_8));
        return request;
    }

    private static String readAll(BufferedReader reader) {
        return reader.lines().collect(Collectors.joining("\n"));
    }
}
