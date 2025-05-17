package com.familynest.controller;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/test")
public class TestController {

    @GetMapping(value = "/connection", produces = MediaType.TEXT_HTML_VALUE)
    public String testConnection() {
        return "<!DOCTYPE html>" +
               "<html>" +
               "<head>" +
               "    <title>Connection Test</title>" +
               "    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">" +
               "    <style>" +
               "        body { font-family: Arial, sans-serif; padding: 20px; }" +
               "        .success { color: green; font-weight: bold; }" +
               "        button { background-color: #4CAF50; color: white; padding: 10px; " +
               "                 border: none; border-radius: 4px; font-size: 16px; cursor: pointer; }" +
               "    </style>" +
               "</head>" +
               "<body>" +
               "    <h1>FamilyNest Connection Test</h1>" +
               "    <p class=\"success\">✅ SUCCESS! Your device can connect to the server.</p>" +
               "    <p>This test confirms that your device can reach the backend server.</p>" +
               "    <p>The Flutter app should now be able to connect properly.</p>" +
               "    <div style=\"margin-top: 20px;\">" +
               "        <a href=\"/api/users/test\"><button>Test API Endpoint</button></a>" +
               "    </div>" +
               "</body>" +
               "</html>";
    }
} 