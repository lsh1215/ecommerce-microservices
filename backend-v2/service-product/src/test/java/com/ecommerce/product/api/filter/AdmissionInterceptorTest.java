package com.ecommerce.product.api.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ecommerce.product.infra.admission.AdmissionRateLimiter;
import com.ecommerce.product.infra.admission.AdmissionRateLimiter.AdmissionDecision;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class AdmissionInterceptorTest {

    @Test
    void modeOff_passesThroughWithoutCallingLimiter() throws Exception {
        AdmissionRateLimiter limiter = mock(AdmissionRateLimiter.class);
        AdmissionInterceptor interceptor = new AdmissionInterceptor(limiter, "off");

        boolean result = interceptor.preHandle(
                new MockHttpServletRequest(), new MockHttpServletResponse(), new Object());

        assertThat(result).isTrue();
        verify(limiter, never()).tryAcquire();
    }

    @Test
    void modeOn_allowedDecision_passesThrough() throws Exception {
        AdmissionRateLimiter limiter = mock(AdmissionRateLimiter.class);
        when(limiter.tryAcquire()).thenReturn(new AdmissionDecision(true, 0));
        AdmissionInterceptor interceptor = new AdmissionInterceptor(limiter, "on");

        MockHttpServletResponse response = new MockHttpServletResponse();
        boolean result = interceptor.preHandle(new MockHttpServletRequest(), response, new Object());

        assertThat(result).isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void modeOn_rejectedDecision_returns429WithRetryAfter() throws Exception {
        AdmissionRateLimiter limiter = mock(AdmissionRateLimiter.class);
        when(limiter.tryAcquire()).thenReturn(new AdmissionDecision(false, 5));
        AdmissionInterceptor interceptor = new AdmissionInterceptor(limiter, "on");

        MockHttpServletResponse response = new MockHttpServletResponse();
        boolean result = interceptor.preHandle(new MockHttpServletRequest(), response, new Object());

        assertThat(result).isFalse();
        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getHeader("Retry-After")).isEqualTo("5");
        assertThat(response.getContentType()).contains("application/json");
        assertThat(response.getContentAsString()).contains("\"success\":false");
    }

    @Test
    void modeIsCaseInsensitive() throws Exception {
        AdmissionRateLimiter limiter = mock(AdmissionRateLimiter.class);
        when(limiter.tryAcquire()).thenReturn(new AdmissionDecision(false, 2));
        AdmissionInterceptor interceptor = new AdmissionInterceptor(limiter, "ON");

        MockHttpServletResponse response = new MockHttpServletResponse();
        boolean result = interceptor.preHandle(new MockHttpServletRequest(), response, new Object());

        assertThat(result).isFalse();
        assertThat(response.getStatus()).isEqualTo(429);
    }
}
