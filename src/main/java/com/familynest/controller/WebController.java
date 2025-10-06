package com.familynest.controller;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@RestController
public class WebController {
    
    @GetMapping(value = "/reset-password", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> resetPassword() {
        try {
            Resource resource = new ClassPathResource("static/reset-password.html");
            String content = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_HTML)
                    .body(content);
        } catch (IOException e) {
            return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_HTML)
                    .body(getFallbackResetPage());
        }
    }
    
    private String getFallbackResetPage() {
        return """
            <!DOCTYPE html>
            <html>
            <head><title>Reset Password</title></head>
            <body>
                <h1>Password Reset</h1>
                <p>Loading reset form...</p>
                <script>
                    window.location.href = '/reset-password.html';
                </script>
            </body>
            </html>
            """;
    }
} 
