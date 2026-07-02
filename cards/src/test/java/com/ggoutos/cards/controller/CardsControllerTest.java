package com.ggoutos.cards.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ggoutos.cards.constants.CardsConstants;
import com.ggoutos.cards.service.ICardsService;
import com.ggoutos.utils.dto.CardsDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.servlet.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(value = CardsController.class, excludeAutoConfiguration = OAuth2ResourceServerAutoConfiguration.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("CardsController Tests")
class CardsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ICardsService cardsService;

    private CardsDto testCardsDto;
    private static final String TEST_MOBILE_NUMBER = "9939321212";
    private static final String TEST_CORRELATION_ID = "test-correlation-id";

    @BeforeEach
    void setUp() {
        testCardsDto = new CardsDto();
        testCardsDto.setMobileNumber(TEST_MOBILE_NUMBER);
        testCardsDto.setCardNumber("4532123456789012");
        testCardsDto.setCardType("Credit Card");
        testCardsDto.setTotalLimit(100000);
        testCardsDto.setAmountUsed(10000);
        testCardsDto.setAvailableAmount(90000);
    }

    @Nested
    @DisplayName("createCard() Tests")
    class CreateCardTests {

        @Test
        @DisplayName("Should create card and return 201")
        void createCard_shouldReturn201() throws Exception {
            // Given
            doNothing().when(cardsService).createCard(TEST_MOBILE_NUMBER);

            // When & Then
            mockMvc.perform(post("/api/create")
                            .param("mobileNumber", TEST_MOBILE_NUMBER))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.statusCode").value(CardsConstants.STATUS_201))
                    .andExpect(jsonPath("$.statusMsg").value(CardsConstants.MESSAGE_201));

            verify(cardsService).createCard(TEST_MOBILE_NUMBER);
        }

        @Test
        @DisplayName("Should return 400 when mobile number is invalid")
        void createCard_shouldReturn400_whenMobileNumberInvalid() throws Exception {
            // When & Then
            mockMvc.perform(post("/api/create")
                            .param("mobileNumber", "123"))
                    .andExpect(status().isBadRequest());

            verify(cardsService, never()).createCard(anyString());
        }
    }

    @Nested
    @DisplayName("fetchCardDetails() Tests")
    class FetchCardTests {

        @Test
        @DisplayName("Should fetch card and return 200")
        void fetchCardDetails_shouldReturn200() throws Exception {
            // Given
            when(cardsService.fetchCard(TEST_MOBILE_NUMBER)).thenReturn(testCardsDto);

            // When & Then
            mockMvc.perform(get("/api/fetch")
                            .param("mobileNumber", TEST_MOBILE_NUMBER)
                            .header("eazybank-correlation-id", TEST_CORRELATION_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.mobileNumber").value(TEST_MOBILE_NUMBER))
                    .andExpect(jsonPath("$.cardNumber").value(testCardsDto.getCardNumber()));

            verify(cardsService).fetchCard(TEST_MOBILE_NUMBER);
        }

        @Test
        @DisplayName("Should return 400 when correlation-id header is missing")
        void fetchCardDetails_shouldReturn400_whenCorrelationIdMissing() throws Exception {
            // When & Then
            mockMvc.perform(get("/api/fetch")
                            .param("mobileNumber", TEST_MOBILE_NUMBER))
                    .andExpect(status().isBadRequest());

            verify(cardsService, never()).fetchCard(anyString());
        }

        @Test
        @DisplayName("Should return 400 when mobile number is invalid")
        void fetchCardDetails_shouldReturn400_whenMobileNumberInvalid() throws Exception {
            // When & Then
            mockMvc.perform(get("/api/fetch")
                            .param("mobileNumber", "123")
                            .header("eazybank-correlation-id", TEST_CORRELATION_ID))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("updateCardDetails() Tests")
    class UpdateCardTests {

        @Test
        @DisplayName("Should update card and return 200")
        void updateCardDetails_shouldReturn200() throws Exception {
            // Given
            when(cardsService.updateCard(any(CardsDto.class))).thenReturn(true);

            // When & Then
            mockMvc.perform(put("/api/update")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(testCardsDto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.statusCode").value(CardsConstants.STATUS_200))
                    .andExpect(jsonPath("$.statusMsg").value(CardsConstants.MESSAGE_200));

            verify(cardsService).updateCard(any(CardsDto.class));
        }

        @Test
        @DisplayName("Should return 417 when update fails")
        void updateCardDetails_shouldReturn417_whenUpdateFails() throws Exception {
            // Given
            when(cardsService.updateCard(any(CardsDto.class))).thenReturn(false);

            // When & Then
            mockMvc.perform(put("/api/update")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(testCardsDto)))
                    .andExpect(status().isExpectationFailed())
                    .andExpect(jsonPath("$.statusCode").value(CardsConstants.STATUS_417));
        }
    }

    @Nested
    @DisplayName("deleteCardDetails() Tests")
    class DeleteCardTests {

        @Test
        @DisplayName("Should delete card and return 200")
        void deleteCardDetails_shouldReturn200() throws Exception {
            // Given
            when(cardsService.deleteCard(TEST_MOBILE_NUMBER)).thenReturn(true);

            // When & Then
            mockMvc.perform(delete("/api/delete")
                            .param("mobileNumber", TEST_MOBILE_NUMBER))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.statusCode").value(CardsConstants.STATUS_200))
                    .andExpect(jsonPath("$.statusMsg").value(CardsConstants.MESSAGE_200));

            verify(cardsService).deleteCard(TEST_MOBILE_NUMBER);
        }

        @Test
        @DisplayName("Should return 400 when mobile number is invalid")
        void deleteCardDetails_shouldReturn400_whenMobileNumberInvalid() throws Exception {
            // When & Then
            mockMvc.perform(delete("/api/delete")
                            .param("mobileNumber", "123"))
                    .andExpect(status().isBadRequest());

            verify(cardsService, never()).deleteCard(anyString());
        }
    }
}
