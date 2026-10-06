package kr.co.mcmp.softwarecatalog.kubernetes.service;

import static org.mockito.Mockito.*;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.Flow;
import org.mockito.ArgumentMatchers;

final class NhnTestHttp {
    final HttpClient client = mock(HttpClient.class);
    final List<HttpRequest> requests = new ArrayList<>();
    final Deque<HttpResponse<String>> responses = new ArrayDeque<>();
    NhnTestHttp() throws Exception {
        when(client.send(any(HttpRequest.class), ArgumentMatchers.<HttpResponse.BodyHandler<String>>any())).thenAnswer(a -> {
            requests.add(a.getArgument(0));
            if (responses.isEmpty()) throw new AssertionError("Unexpected HTTP request");
            return responses.removeFirst();
        });
    }
    @SuppressWarnings("unchecked")
    void respond(int status, String body) {
        var response = (HttpResponse<String>) mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status); when(response.body()).thenReturn(body); responses.add(response);
    }
    static String body(HttpRequest request) {
        var output = new java.io.ByteArrayOutputStream();
        request.bodyPublisher().orElseThrow().subscribe(new Flow.Subscriber<ByteBuffer>() {
            public void onSubscribe(Flow.Subscription s) { s.request(Long.MAX_VALUE); }
            public void onNext(ByteBuffer buffer) { var data = new byte[buffer.remaining()]; buffer.get(data); output.writeBytes(data); }
            public void onError(Throwable t) { throw new AssertionError(t); }
            public void onComplete() { }
        });
        return output.toString(StandardCharsets.UTF_8);
    }
    static String loginBody(String tenant, String endpoint) {
        return """
                {"access":{"token":{"id":"fake-nhn-token","expires":"%s","tenant":{"id":"%s"}},
                "serviceCatalog":[{"type":"container-infra","endpoints":[{"region":"KR1","publicURL":"%s"}]}]}}
                """.formatted(java.time.Instant.now().plusSeconds(3600), tenant, endpoint);
    }
}
