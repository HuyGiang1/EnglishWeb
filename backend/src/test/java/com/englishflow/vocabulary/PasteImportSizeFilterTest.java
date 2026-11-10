package com.englishflow.vocabulary;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.assertj.core.api.Assertions.*;

class PasteImportSizeFilterTest {
    private final PasteImportSizeFilter filter = new PasteImportSizeFilter(new ObjectMapper().findAndRegisterModules());
    @Test void chunkedRequestIsBoundedWithoutContentLength() throws Exception {
        var request = new MockHttpServletRequest("POST","/api/v1/vocabulary/bulk-import/paste/preview") {
            @Override public long getContentLengthLong() { return -1; }
        };
        request.setContent(new byte[PasteImportParser.MAX_REQUEST_BYTES+1]);
        var response=new MockHttpServletResponse(); var called=new AtomicBoolean();
        filter.doFilter(request,response,(a,b)->called.set(true));
        assertThat(response.getStatus()).isEqualTo(413); assertThat(called.get()).isFalse();
        assertThat(response.getContentAsString()).contains("PAYLOAD_TOO_LARGE");
    }
    @Test void validBodyIsPassedIntactToJsonDecoder() throws Exception {
        var request=new MockHttpServletRequest("POST","/api/v1/vocabulary/bulk-import/paste/validate");
        String body="{\"text\":\"nghĩa tiếng Việt\"}";
        request.setContent(body.getBytes(StandardCharsets.UTF_8));
        filter.doFilter(request,new MockHttpServletResponse(),(a,b)-> {
            assertThat(new String(a.getInputStream().readAllBytes(),StandardCharsets.UTF_8)).isEqualTo(body);
        });
    }
}
