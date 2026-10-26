package com.example.javabackend.service;

/**
 * T-04 integration boundary; deliberately has no placeholder production bean.
 * Once T-04 is merged, register a bean that resolves the authenticated server
 * SecurityContext principal to the existing CustomerAccount database ID, reusing
 * CustomerAccountRepository.findByUsername(authentication.getName()) if needed.
 * The locally available CustomerAccount currently lacks an ID accessor; that
 * must be supplied by T-04 or an existing repository ID projection.
 * Reject missing/anonymous authentication with HTTP 401. Never obtain identity
 * from request JSON, query parameters, or a fixed training account.
 * Retain T-04's authenticated /api/quotes routes and CSRF protection for POSTs.
 * Until this bean and T-04 are integrated, full application startup is blocked.
 */
public interface CurrentCustomerIdProvider {
    long requireCurrentCustomerId();
}
