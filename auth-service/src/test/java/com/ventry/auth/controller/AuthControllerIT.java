package com.ventry.auth.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Transactional
class AuthControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void registerThenLogin_issuesJwtBothTimes() throws Exception {
        String email = "flow@ventry.com";
        String registerJson = """
                {"email":"%s","password":"password123"}
                """.formatted(email);

        mockMvc.perform(post("/api/auth/register")
                        .contentType("application/json")
                        .content(registerJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.role").value("CUSTOMER"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content(registerJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.role").value("CUSTOMER"));
    }

    @Test
    void register_rejectsDuplicateEmailWith409() throws Exception {
        String registerJson = """
                {"email":"dup@ventry.com","password":"password123"}
                """;

        mockMvc.perform(post("/api/auth/register")
                        .contentType("application/json")
                        .content(registerJson))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/auth/register")
                        .contentType("application/json")
                        .content(registerJson))
                .andExpect(status().isConflict());
    }

    @Test
    void login_rejectsWrongPasswordWith401() throws Exception {
        String registerJson = """
                {"email":"wrongpass@ventry.com","password":"password123"}
                """;
        mockMvc.perform(post("/api/auth/register")
                        .contentType("application/json")
                        .content(registerJson))
                .andExpect(status().isCreated());

        String badLoginJson = """
                {"email":"wrongpass@ventry.com","password":"incorrect"}
                """;
        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content(badLoginJson))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void register_rejectsInvalidPayloadWith400() throws Exception {
        String invalidJson = """
                {"email":"not-an-email","password":"short"}
                """;
        mockMvc.perform(post("/api/auth/register")
                        .contentType("application/json")
                        .content(invalidJson))
                .andExpect(status().isBadRequest());
    }
}
