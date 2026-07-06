package com.example.demo;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.util.Map;

@RestController
public class UrlController {

    @Autowired
    private UrlService service;

    @PostMapping("/shorten")
    public ResponseEntity<String> shorten(@RequestBody Map<String, String> body) {
        String shortCode = service.shortenUrl(body.get("url"));
        return ResponseEntity.ok("http://localhost:8080/" + shortCode);
    }

    @GetMapping("/{code}")
    public ResponseEntity<Void> redirect(@PathVariable String code) {
        String longUrl = service.getLongUrl(code);
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(longUrl))
                .build();
    }
}