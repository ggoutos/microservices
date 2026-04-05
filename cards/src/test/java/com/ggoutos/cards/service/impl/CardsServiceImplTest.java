package com.ggoutos.cards.service.impl;

import com.ggoutos.cards.constants.CardsConstants;
import com.ggoutos.cards.entity.Cards;
import com.ggoutos.cards.exception.CardAlreadyExistsException;
import com.ggoutos.cards.exception.ResourceNotFoundException;
import com.ggoutos.cards.repository.CardsRepository;
import com.ggoutos.utils.dto.CardsDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CardsServiceImpl Tests")
class CardsServiceImplTest {

    @Mock
    private CardsRepository cardsRepository;

    @InjectMocks
    private CardsServiceImpl cardsService;

    private Cards testCard;
    private CardsDto testCardsDto;
    private static final String TEST_MOBILE_NUMBER = "9939321212";

    @BeforeEach
    void setUp() {
        testCard = new Cards();
        testCard.setCardId(1L);
        testCard.setMobileNumber(TEST_MOBILE_NUMBER);
        testCard.setCardNumber("4532123456789012");
        testCard.setCardType(CardsConstants.CREDIT_CARD);
        testCard.setTotalLimit(CardsConstants.NEW_CARD_LIMIT);
        testCard.setAmountUsed(0);
        testCard.setAvailableAmount(CardsConstants.NEW_CARD_LIMIT);

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
        @DisplayName("Should create card successfully")
        void createCard_shouldSaveCard() {
            // Given
            when(cardsRepository.findByMobileNumber(TEST_MOBILE_NUMBER)).thenReturn(Optional.empty());
            when(cardsRepository.save(any(Cards.class))).thenReturn(testCard);

            // When
            cardsService.createCard(TEST_MOBILE_NUMBER);

            // Then
            verify(cardsRepository).findByMobileNumber(TEST_MOBILE_NUMBER);
            verify(cardsRepository).save(argThat(card ->
                    card.getMobileNumber().equals(TEST_MOBILE_NUMBER) &&
                    card.getCardType().equals(CardsConstants.CREDIT_CARD) &&
                    card.getTotalLimit().equals(CardsConstants.NEW_CARD_LIMIT) &&
                    card.getAmountUsed().equals(0) &&
                    card.getAvailableAmount().equals(CardsConstants.NEW_CARD_LIMIT) &&
                    card.getCardNumber().matches("\\d{16}")
            ));
        }

        @Test
        @DisplayName("Should throw CardAlreadyExistsException when card already exists")
        void createCard_shouldThrowException_whenCardAlreadyExists() {
            // Given
            when(cardsRepository.findByMobileNumber(TEST_MOBILE_NUMBER)).thenReturn(Optional.of(testCard));

            // When & Then
            assertThatThrownBy(() -> cardsService.createCard(TEST_MOBILE_NUMBER))
                    .isInstanceOf(CardAlreadyExistsException.class)
                    .hasMessageContaining("Card already registered with given mobileNumber");

            verify(cardsRepository, never()).save(any(Cards.class));
        }

        @Test
        @DisplayName("Should generate 16-digit card number")
        void createCard_shouldGenerate16DigitCardNumber() {
            // Given
            when(cardsRepository.findByMobileNumber(TEST_MOBILE_NUMBER)).thenReturn(Optional.empty());
            when(cardsRepository.save(any(Cards.class))).thenAnswer(invocation -> {
                Cards savedCard = invocation.getArgument(0);
                assertThat(savedCard.getCardNumber()).hasSize(16);
                assertThat(savedCard.getCardNumber()).matches("\\d{16}");
                return savedCard;
            });

            // When
            cardsService.createCard(TEST_MOBILE_NUMBER);

            // Then
            verify(cardsRepository).save(any(Cards.class));
        }
    }

    @Nested
    @DisplayName("fetchCard() Tests")
    class FetchCardTests {

        @Test
        @DisplayName("Should fetch card successfully")
        void fetchCard_shouldReturnCardsDto() {
            // Given
            when(cardsRepository.findByMobileNumber(TEST_MOBILE_NUMBER)).thenReturn(Optional.of(testCard));

            // When
            CardsDto result = cardsService.fetchCard(TEST_MOBILE_NUMBER);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getMobileNumber()).isEqualTo(TEST_MOBILE_NUMBER);
            assertThat(result.getCardNumber()).isEqualTo(testCard.getCardNumber());
            assertThat(result.getCardType()).isEqualTo(testCard.getCardType());
            assertThat(result.getTotalLimit()).isEqualTo(testCard.getTotalLimit());
            assertThat(result.getAmountUsed()).isEqualTo(testCard.getAmountUsed());
            assertThat(result.getAvailableAmount()).isEqualTo(testCard.getAvailableAmount());

            verify(cardsRepository).findByMobileNumber(TEST_MOBILE_NUMBER);
        }

        @Test
        @DisplayName("Should throw ResourceNotFoundException when card not found")
        void fetchCard_shouldThrowException_whenCardNotFound() {
            // Given
            when(cardsRepository.findByMobileNumber(TEST_MOBILE_NUMBER)).thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> cardsService.fetchCard(TEST_MOBILE_NUMBER))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Card not found");
        }
    }

    @Nested
    @DisplayName("updateCard() Tests")
    class UpdateCardTests {

        @Test
        @DisplayName("Should update card successfully")
        void updateCard_shouldReturnTrue() {
            // Given
            when(cardsRepository.findByCardNumber(testCardsDto.getCardNumber())).thenReturn(Optional.of(testCard));
            when(cardsRepository.save(any(Cards.class))).thenReturn(testCard);

            // When
            boolean result = cardsService.updateCard(testCardsDto);

            // Then
            assertThat(result).isTrue();
            verify(cardsRepository).findByCardNumber(testCardsDto.getCardNumber());
            verify(cardsRepository).save(any(Cards.class));
        }

        @Test
        @DisplayName("Should throw ResourceNotFoundException when card not found")
        void updateCard_shouldThrowException_whenCardNotFound() {
            // Given
            when(cardsRepository.findByCardNumber(testCardsDto.getCardNumber())).thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> cardsService.updateCard(testCardsDto))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Card not found");
        }
    }

    @Nested
    @DisplayName("deleteCard() Tests")
    class DeleteCardTests {

        @Test
        @DisplayName("Should delete card successfully")
        void deleteCard_shouldReturnTrue() {
            // Given
            when(cardsRepository.findByMobileNumber(TEST_MOBILE_NUMBER)).thenReturn(Optional.of(testCard));

            // When
            boolean result = cardsService.deleteCard(TEST_MOBILE_NUMBER);

            // Then
            assertThat(result).isTrue();
            verify(cardsRepository).findByMobileNumber(TEST_MOBILE_NUMBER);
            verify(cardsRepository).deleteById(testCard.getCardId());
        }

        @Test
        @DisplayName("Should throw ResourceNotFoundException when card not found")
        void deleteCard_shouldThrowException_whenCardNotFound() {
            // Given
            when(cardsRepository.findByMobileNumber(TEST_MOBILE_NUMBER)).thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> cardsService.deleteCard(TEST_MOBILE_NUMBER))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Card not found");

            verify(cardsRepository, never()).deleteById(anyLong());
        }
    }
}
