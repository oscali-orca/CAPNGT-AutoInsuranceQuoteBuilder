package com.example.javabackend.controller;

import com.example.javabackend.dto.DuplicateQuoteResponse;
import com.example.javabackend.dto.SaveQuoteRequest;
import com.example.javabackend.dto.SavedQuoteResponse;
import com.example.javabackend.service.SavedQuoteService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/quotes")
public class SavedQuoteController {
    private final SavedQuoteService service;
    public SavedQuoteController(SavedQuoteService service) { this.service = service; }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SavedQuoteResponse save(@RequestBody SaveQuoteRequest request) {
        return service.save(request);
    }

    @GetMapping
    public List<SavedQuoteResponse> list() { return service.list(); }

    @GetMapping("/{id}")
    public SavedQuoteResponse get(@PathVariable long id) { return service.get(id); }

    @PostMapping("/{id}/duplicate")
    public DuplicateQuoteResponse duplicate(@PathVariable long id) {
        return service.duplicate(id);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ProblemDetail> handleInvalidInput(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(
                ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemDetail> handleMalformedRequest() {
        return ResponseEntity.badRequest().body(ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "Request body must contain valid JSON, a UUID saveRequestKey, and whole-number pricing inputs."));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ProblemDetail> handleStatus(ResponseStatusException exception) {
        return ResponseEntity.status(exception.getStatusCode()).body(
                ProblemDetail.forStatusAndDetail(exception.getStatusCode(), exception.getReason()));
    }
}
