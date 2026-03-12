package com.atex.desk.api.config;

import com.atex.desk.api.service.RequestMetricsService;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Servlet filter that captures request/response metrics into RequestMetricsService.
 * Registered at order -1 (before auth) to capture all requests including auth failures.
 * Wraps the response to count bytes written for accurate response size tracking.
 */
public class RequestMetricsFilter implements Filter {

    private final RequestMetricsService metricsService;

    public RequestMetricsFilter(RequestMetricsService metricsService) {
        this.metricsService = metricsService;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (!(request instanceof HttpServletRequest req) || !(response instanceof HttpServletResponse resp)) {
            chain.doFilter(request, response);
            return;
        }

        String uri = req.getRequestURI();
        if (isExcluded(uri)) {
            chain.doFilter(request, response);
            return;
        }

        CountingResponseWrapper wrapper = new CountingResponseWrapper(resp);
        long start = System.nanoTime();
        try {
            chain.doFilter(request, wrapper);
        } finally {
            long durationMs = (System.nanoTime() - start) / 1_000_000;
            String user = (String) req.getAttribute("desk.auth.user");
            String contentType = wrapper.getContentType();
            long bytesWritten = wrapper.getBytesWritten();

            metricsService.record(
                    req.getMethod(),
                    uri,
                    req.getQueryString(),
                    wrapper.getStatus(),
                    durationMs,
                    user,
                    contentType,
                    bytesWritten
            );
        }
    }

    private boolean isExcluded(String uri) {
        return uri.startsWith("/actuator")
                || uri.startsWith("/admin/requests")
                || uri.startsWith("/admin/cache")
                || uri.startsWith("/swagger")
                || uri.startsWith("/v3/api-docs")
                || uri.startsWith("/api/status")
                || uri.startsWith("/api/endpoints")
                || uri.endsWith(".html")
                || uri.endsWith(".css")
                || uri.endsWith(".js")
                || uri.endsWith(".ico")
                || uri.endsWith(".png")
                || uri.endsWith(".jpg")
                || uri.endsWith(".svg")
                || uri.endsWith(".woff")
                || uri.endsWith(".woff2");
    }

    /**
     * Response wrapper that counts bytes written through both OutputStream and Writer.
     */
    private static class CountingResponseWrapper extends HttpServletResponseWrapper {
        private final AtomicLong bytesWritten = new AtomicLong(0);
        private CountingOutputStream countingStream;
        private PrintWriter countingWriter;

        CountingResponseWrapper(HttpServletResponse response) {
            super(response);
        }

        long getBytesWritten() {
            if (countingWriter != null) countingWriter.flush();
            return bytesWritten.get();
        }

        @Override
        public ServletOutputStream getOutputStream() throws IOException {
            if (countingStream == null) {
                countingStream = new CountingOutputStream(super.getOutputStream(), bytesWritten);
            }
            return countingStream;
        }

        @Override
        public PrintWriter getWriter() throws IOException {
            if (countingWriter == null) {
                countingWriter = new PrintWriter(super.getWriter()) {
                    @Override
                    public void write(char[] buf, int off, int len) {
                        super.write(buf, off, len);
                        bytesWritten.addAndGet(len); // approximate — char != byte for non-ASCII
                    }

                    @Override
                    public void write(String s, int off, int len) {
                        super.write(s, off, len);
                        bytesWritten.addAndGet(len);
                    }

                    @Override
                    public void write(int c) {
                        super.write(c);
                        bytesWritten.incrementAndGet();
                    }
                };
            }
            return countingWriter;
        }
    }

    private static class CountingOutputStream extends ServletOutputStream {
        private final ServletOutputStream delegate;
        private final AtomicLong counter;

        CountingOutputStream(ServletOutputStream delegate, AtomicLong counter) {
            this.delegate = delegate;
            this.counter = counter;
        }

        @Override
        public void write(int b) throws IOException {
            delegate.write(b);
            counter.incrementAndGet();
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            delegate.write(b, off, len);
            counter.addAndGet(len);
        }

        @Override
        public boolean isReady() { return delegate.isReady(); }

        @Override
        public void setWriteListener(WriteListener listener) { delegate.setWriteListener(listener); }

        @Override
        public void flush() throws IOException { delegate.flush(); }

        @Override
        public void close() throws IOException { delegate.close(); }
    }
}
