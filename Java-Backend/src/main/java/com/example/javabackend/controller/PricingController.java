package com.example.javabackend.controller;

import com.example.javabackend.dto.PricingRequest;
import com.example.javabackend.dto.PricingResponse;
import com.example.javabackend.service.PricingService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/pricing")
public class PricingController {
    private final PricingService pricingService;

    public PricingController(PricingService pricingService) {
        this.pricingService = pricingService;
    }

    @PostMapping("/compare")
    public PricingResponse compare(@RequestBody PricingRequest request) {
        return pricingService.compare(request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ProblemDetail> handleInvalidInput(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(
                ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemDetail> handleMalformedRequest() {
        return ResponseEntity.badRequest().body(ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "Request body must contain valid JSON with whole-number input values."));
    }
}
