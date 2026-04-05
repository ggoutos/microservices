package com.ggoutos.cards.exception;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ggoutos.cards.controller.CardsController;
import com.ggoutos.cards.service.ICardsService;
import com.ggoutos.utils.dto.CardsDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CardsController.class)
@DisplayName("GlobalExceptionHandler Tests")
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ICardsService cardsService;

    @Nested
    @DisplayName("ResourceNotFoundException Tests")
    class ResourceNotFoundTests {

        @Test
        @DisplayName("Should return 404 when ResourceNotFoundException is thrown")
        void handleResourceNotFoundException_shouldReturn404() throws Exception {
            // Given
            when(cardsService.fetchCard("0000000000"))
                    .thenThrow(new ResourceNotFoundException("Card", "mobileNumber", "0000000000"));

            // When & Then
            mockMvc.perform(get("/api/fetch")
                            .param("mobileNumber", "0000000000")
                            .header("eazybank-correlation-id", "test-id"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.errorCode").value("404 NOT_FOUND"))
                    .andExpect(jsonPath("$.errorMessage").value("Card not found with the given input data mobileNumber : '0000000000'"));
        }
    }

    @Nested
    @DisplayName("CardAlreadyExistsException Tests")
    class CardAlreadyExistsTests {

        @Test
        @DisplayName("Should return 400 when CardAlreadyExistsException is thrown")
        void handleCardAlreadyExistsException_shouldReturn400() throws Exception {
            // Given
            doThrow(new CardAlreadyExistsException("Card already registered with given mobileNumber 9939321212"))
                    .when(cardsService).createCard("9939321212");

            // When & Then
            mockMvc.perform(post("/api/create")
                            .param("mobileNumber", "9939321212"))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("Validation Error Tests")
    class ValidationErrorTests {

        @Test
        @DisplayName("Should return 400 when validation fails")
        void handleValidationErrors_shouldReturn400() throws Exception {
            // Given
            CardsDto cardsDto = new CardsDto();
            cardsDto.setCardNumber("123"); // Invalid

            // When & Then
            mockMvc.perform(put("/api/update")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(cardsDto)))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("Generic Exception Tests")
    class GenericExceptionTests {

        @Test
        @DisplayName("Should return 500 when generic exception is thrown")
        void handleGenericException_shouldReturn500() throws Exception {
            // Given
            when(cardsService.fetchCard(anyString()))
                    .thenThrow(new RuntimeException("Unexpected error"));

            // When & Then
            mockMvc.perform(get("/api/fetch")
                            .param("mobileNumber", "9939321212")
                            .header("eazybank-correlation-id", "test-id"))
                    .andExpect(status().isInternalServerError());
        }
    }
}
