package com.englishflow.vocabulary;

import com.englishflow.shared.error.ApiErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.*;
import java.nio.charset.StandardCharsets;

@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class PasteImportSizeFilter extends OncePerRequestFilter {
    private final ObjectMapper mapper;
    public PasteImportSizeFilter(ObjectMapper mapper) { this.mapper = mapper; }
    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equals(request.getMethod()) || !request.getRequestURI().startsWith("/api/v1/vocabulary/bulk-import/paste/");
    }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {
        if (request.getContentLengthLong() > PasteImportParser.MAX_REQUEST_BYTES) { reject(response); return; }
        byte[] bytes = request.getInputStream().readNBytes(PasteImportParser.MAX_REQUEST_BYTES + 1);
        if (bytes.length > PasteImportParser.MAX_REQUEST_BYTES) { reject(response); return; }
        chain.doFilter(new HttpServletRequestWrapper(request) {
            @Override public ServletInputStream getInputStream() {
                ByteArrayInputStream input = new ByteArrayInputStream(bytes);
                return new ServletInputStream() {
                    @Override public int read() { return input.read(); }
                    @Override public int read(byte[] buffer, int offset, int length) { return input.read(buffer, offset, length); }
                    @Override public boolean isFinished() { return input.available() == 0; }
                    @Override public boolean isReady() { return true; }
                    @Override public void setReadListener(ReadListener listener) { throw new IllegalStateException("Synchronous request body."); }
                };
            }
            @Override public BufferedReader getReader() { return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8)); }
        }, response);
    }
    private void reject(HttpServletResponse response) throws IOException {
        response.setStatus(413); response.setContentType("application/json");
        mapper.writeValue(response.getOutputStream(), ApiErrorResponse.of("PAYLOAD_TOO_LARGE", "Import request exceeds 4 MiB.", 413));
    }
}
