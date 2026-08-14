package com.neganote.bankapi.versioning;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

@Component
public class DeprecationHeaderInterceptor implements HandlerInterceptor {

    /** When v1 will be retired. Format: RFC 7231 HTTP-date. */
    private static final String SUNSET_DATE_HEADER =
            // 6 months from a fixed date - hardcode for the sprint
            "Wed, 01 Jul 2026 00:00:00 GMT";

    @Override
    public void postHandle(
            HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull Object handler,
            ModelAndView modelAndView) {
        String path = request.getRequestURI();
        if (path.startsWith("/api/v1/")) {
            response.setHeader("Deprecation", "true");
            response.setHeader("Sunset", SUNSET_DATE_HEADER);

            // Optional: point to v2 equivalent where one exists
            String successor = mapV1ToV2(path);
            response.setHeader("Link", "<" + successor + ">; rel=\"successor-version\"");
        }
    }

    private String mapV1ToV2(String v1Path) {
        // Already checked for /api/v1/ prefix in caller, so we can just replace it with /api/v2/,
        // no need to have to check for anything to be able to return null.
        return v1Path.replaceFirst("/api/v1/", "/api/v2/");
    }
}
