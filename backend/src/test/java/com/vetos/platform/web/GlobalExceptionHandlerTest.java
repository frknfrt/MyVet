package com.vetos.platform.web;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {

    @RestController
    static class ProbeController {

        @GetMapping("/probe/param")
        String requiredParam(@RequestParam("q") String q) {
            return q;
        }

        @GetMapping("/probe/typed/{id}")
        String typed(@PathVariable("id") UUID id) {
            return id.toString();
        }

        @PostMapping(value = "/probe/json", consumes = MediaType.APPLICATION_JSON_VALUE)
        String json(@RequestBody Map<String, Object> body) {
            return "ok";
        }

        @GetMapping("/probe/too-large")
        String tooLarge() {
            throw new MaxUploadSizeExceededException(15L * 1024 * 1024);
        }

        @GetMapping("/probe/no-resource")
        String noResource() throws NoResourceFoundException {
            throw new NoResourceFoundException(HttpMethod.GET, "probe/no-resource");
        }

        @GetMapping("/probe/boom")
        String boom() {
            throw new IllegalStateException("gizli ic detay: db sifresi");
        }
    }

    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new ProbeController())
        .setControllerAdvice(new GlobalExceptionHandler())
        .build();

    @Test
    void should_return400_when_requiredQueryParamMissing() throws Exception {
        mockMvc.perform(get("/probe/param"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errorCode").value("MISSING_PARAMETER"))
            .andExpect(jsonPath("$.path").value("/probe/param"));
    }

    @Test
    void should_return400_when_pathVariableHasWrongType() throws Exception {
        mockMvc.perform(get("/probe/typed/uuid-degil"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errorCode").value("INVALID_PARAMETER"));
    }

    @Test
    void should_return405WithAllowHeader_when_methodNotSupported() throws Exception {
        mockMvc.perform(delete("/probe/param"))
            .andExpect(status().isMethodNotAllowed())
            .andExpect(header().string("Allow", containsString("GET")))
            .andExpect(jsonPath("$.errorCode").value("METHOD_NOT_ALLOWED"));
    }

    @Test
    void should_return415_when_contentTypeNotSupported() throws Exception {
        mockMvc.perform(post("/probe/json").contentType(MediaType.TEXT_PLAIN).content("x"))
            .andExpect(status().isUnsupportedMediaType())
            .andExpect(jsonPath("$.errorCode").value("UNSUPPORTED_MEDIA_TYPE"));
    }

    @Test
    void should_return413_when_uploadTooLarge() throws Exception {
        mockMvc.perform(get("/probe/too-large"))
            .andExpect(status().isPayloadTooLarge())
            .andExpect(jsonPath("$.errorCode").value("PAYLOAD_TOO_LARGE"));
    }

    @Test
    void should_return404_when_noResourceFound() throws Exception {
        mockMvc.perform(get("/probe/no-resource"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.errorCode").value("NOT_FOUND"));
    }

    @Test
    void should_return500WithoutLeakingMessage_when_unexpectedException() throws Exception {
        mockMvc.perform(get("/probe/boom"))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.errorCode").value("INTERNAL_ERROR"))
            .andExpect(jsonPath("$.message").value(not(containsString("gizli"))));
    }
}
