package br.com.fiap.workshop_management_system.identity;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CachedBodyHttpServletRequestTest {

    private static final String BODY = "{\"decisions\":[{\"serviceExecutionId\":\"abc\",\"decision\":\"APPROVED\"}]}";

    @Test
    void getInputStreamReturnsTheFullBodyOnEveryCall() throws IOException {
        CachedBodyHttpServletRequest request = new CachedBodyHttpServletRequest(requestWithBody(BODY));

        byte[] first = request.getInputStream().readAllBytes();
        byte[] second = request.getInputStream().readAllBytes();

        assertArrayEquals(BODY.getBytes(StandardCharsets.UTF_8), first);
        assertArrayEquals(first, second);
    }

    @Test
    void getReaderAfterGetInputStreamStillReturnsTheFullBody() throws IOException {
        CachedBodyHttpServletRequest request = new CachedBodyHttpServletRequest(requestWithBody(BODY));

        request.getInputStream().readAllBytes();

        assertEquals(BODY, readAll(request.getReader()));
    }

    @Test
    void readerHonorsTheRequestCharacterEncoding() throws IOException {
        String body = "{\"note\":\"orçamento aprovado\"}";
        MockHttpServletRequest original = new MockHttpServletRequest();
        original.setCharacterEncoding(StandardCharsets.ISO_8859_1.name());
        original.setContent(body.getBytes(StandardCharsets.ISO_8859_1));

        CachedBodyHttpServletRequest request = new CachedBodyHttpServletRequest(original);

        assertEquals(body, readAll(request.getReader()));
    }

    @Test
    void emptyBodyIsReadWithoutFailing() throws IOException {
        CachedBodyHttpServletRequest request = new CachedBodyHttpServletRequest(new MockHttpServletRequest());

        assertEquals(0, request.getInputStream().readAllBytes().length);
        assertEquals("", readAll(request.getReader()));
        assertTrue(request.getInputStream().isFinished());
    }

    @Test
    void bodyReturnsADefensiveCopy() throws IOException {
        CachedBodyHttpServletRequest request = new CachedBodyHttpServletRequest(requestWithBody(BODY));

        request.body()[0] = 'X';

        assertArrayEquals(BODY.getBytes(StandardCharsets.UTF_8), request.body());
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
