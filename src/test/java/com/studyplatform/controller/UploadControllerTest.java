package com.studyplatform.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UploadControllerTest {
    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private Path uploadedFile;

    @BeforeEach
    void setup() throws Exception {
        Object controller = Class.forName("com.studyplatform.controller.UploadController")
                .getConstructor()
                .newInstance();
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @AfterEach
    void cleanup() throws Exception {
        if (uploadedFile != null) {
            Files.deleteIfExists(uploadedFile);
        }
    }

    @Test
    void uploadImageStoresFileAndReturnsUrl() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "sample.png",
                "image/png",
                new byte[]{(byte) 0x89, 'P', 'N', 'G'}
        );

        String body = mockMvc.perform(multipart("/api/uploads/images").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imageUrl").value(org.hamcrest.Matchers.startsWith("/uploads/chat/")))
                .andExpect(jsonPath("$.fileName").value("sample.png"))
                .andExpect(jsonPath("$.fileSize").value(4))
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode json = objectMapper.readTree(body);
        String storedName = json.get("imageUrl").asText().replace("/uploads/chat/", "");
        uploadedFile = Path.of("uploads", "chat", storedName).toAbsolutePath().normalize();
        org.assertj.core.api.Assertions.assertThat(Files.exists(uploadedFile)).isTrue();
    }

    @Test
    void uploadImageRejectsNonImageFiles() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "note.txt",
                "text/plain",
                "hello".getBytes()
        );

        mockMvc.perform(multipart("/api/uploads/images").file(file))
                .andExpect(status().isBadRequest());
    }
}
