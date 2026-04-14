package com.yeven.thread.dag.demo.quote.controller;

import com.yeven.thread.dag.demo.quote.dto.QuoteRequest;
import com.yeven.thread.dag.demo.quote.dto.QuoteResponse;
import com.yeven.thread.dag.demo.quote.service.QuoteService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/quotes")
public class QuoteController {

    private final QuoteService quoteService;

    public QuoteController(QuoteService quoteService) {
        this.quoteService = quoteService;
    }

    @PostMapping("/preview")
    public Mono<QuoteResponse> preview(@Valid @RequestBody QuoteRequest request) {
        return Mono.fromFuture(quoteService.previewQuote().apply(request));
    }
}
