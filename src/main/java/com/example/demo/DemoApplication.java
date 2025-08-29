package com.example.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.util.StreamUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@SpringBootApplication
public class DemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }

    // This bean runs after app starts. We use @Value to inject values from application.yml
    @Bean
    ApplicationRunner run(
            @Value("${bfhl.name}") String name,
            @Value("${bfhl.regNo}") String regNo,
            @Value("${bfhl.email}") String email,
            @Value("${bfhl.final-sql-path}") Resource sqlFile
    ) {
        return args -> {
            WebClient client = WebClient.builder().build();

            // 1) call generateWebhook API
            String genUrl = "https://bfhldevapigw.healthrx.co.in/hiring/generateWebhook/JAVA";
            Map<String, String> reqBody = new HashMap<>();
            reqBody.put("name", name);
            reqBody.put("regNo", regNo);
            reqBody.put("email", email);

            System.out.println("Calling generateWebhook...");
            Map resp = client.post()
                    .uri(genUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(reqBody)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            if (resp == null) {
                throw new IllegalStateException("No response from generateWebhook");
            }

            String webhook = (String) resp.get("webhook");
            String token = (String) resp.get("accessToken");

            // Print which question link applies (odd/even rule)
            System.out.println("Question link to open based on regNo: " + questionLinkForRegNo(regNo));

            System.out.println("Received webhook: " + webhook);
            System.out.println("Received accessToken: " + (token != null ? "[RECEIVED]" : "[NULL]"));

            // 2) read SQL from resource file
            String finalSql = StreamUtils.copyToString(sqlFile.getInputStream(), StandardCharsets.UTF_8).trim();
            if (finalSql.isBlank()) {
                throw new IllegalStateException("final-sql.sql is empty. Put final SQL in src/main/resources/final-sql.sql");
            }
            System.out.println("Final SQL loaded (first 200 chars): " + finalSql.substring(0, Math.min(200, finalSql.length())));

            // 3) submit the SQL to the webhook
            if (webhook == null || webhook.isBlank()) {
                System.out.println("Webhook is empty — fallback to testWebhook URL per PDF");
                webhook = "https://bfhldevapigw.healthrx.co.in/hiring/testWebhook/JAVA";
            }

            Map<String, String> submitPayload = new HashMap<>();
            submitPayload.put("finalQuery", finalSql);

            try {
                String submitResponse = client.post()
                        .uri(webhook)
                        .header(HttpHeaders.AUTHORIZATION, token == null ? "" : token) // try token raw first
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(submitPayload)
                        .retrieve()
                        .bodyToMono(String.class)
                        .block();
                System.out.println("Submission response: " + submitResponse);
            } catch (Exception ex) {
                System.out.println("Raw token submit failed (will retry with 'Bearer ' prefix). Error: " + ex.getMessage());
                // retry with "Bearer " prefix
                String submitResponse2 = client.post()
                        .uri(webhook)
                        .header(HttpHeaders.AUTHORIZATION, token == null ? "" : "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(submitPayload)
                        .retrieve()
                        .bodyToMono(String.class)
                        .block();
                System.out.println("Submission response (with Bearer): " + submitResponse2);
            }

            System.out.println("Flow complete.");
        };
    }

    // helper: determines odd/even link based on last two digits of regNo
    private static String questionLinkForRegNo(String regNo) {
        String digits = regNo.replaceAll("\\D+", "");
        if (digits.length() < 2) return "Cannot determine (regNo too short)";
        int n = Integer.parseInt(digits.substring(digits.length() - 2));
        boolean isOdd = (n % 2) == 1;
        if (isOdd) {
            return "Question 1 (Odd): https://drive.google.com/file/d/1IeSI6l6KoSQAFfRihIT9tEDICtoz-G/view";
        } else {
            return "Question 2 (Even): https://drive.google.com/file/d/143MR5cLFrlNEuHzzWJ5RHnEWuijuM9X/view";
        }
    }
}
