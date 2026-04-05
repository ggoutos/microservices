package com.ggoutos.cards.repository;

import com.ggoutos.cards.entity.Cards;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@DisplayName("CardsRepository Tests")
class CardsRepositoryTest {

    @Autowired
    private CardsRepository cardsRepository;

    private Cards testCard;
    private static final String TEST_MOBILE_NUMBER = "9999999999";

    @BeforeEach
    void setUp() {
        testCard = new Cards();
        testCard.setMobileNumber(TEST_MOBILE_NUMBER);
        testCard.setCardNumber("4532123456789012");
        testCard.setCardType("Credit Card");
        testCard.setTotalLimit(100000);
        testCard.setAmountUsed(0);
        testCard.setAvailableAmount(100000);
        testCard = cardsRepository.save(testCard);
    }

    @Nested
    @DisplayName("findByMobileNumber() Tests")
    class FindByMobileNumberTests {

        @Test
        @DisplayName("Should return card when mobile number exists")
        void findByMobileNumber_shouldReturnCard() {
            // When
            Optional<Cards> result = cardsRepository.findByMobileNumber(TEST_MOBILE_NUMBER);

            // Then
            assertThat(result).isPresent();
            assertThat(result.get().getMobileNumber()).isEqualTo(TEST_MOBILE_NUMBER);
            assertThat(result.get().getCardNumber()).isEqualTo(testCard.getCardNumber());
        }

        @Test
        @DisplayName("Should return empty when mobile number does not exist")
        void findByMobileNumber_shouldReturnEmpty_whenCardNotFound() {
            // When
            Optional<Cards> result = cardsRepository.findByMobileNumber("0000000000");

            // Then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("findByCardNumber() Tests")
    class FindByCardNumberTests {

        @Test
        @DisplayName("Should return card when card number exists")
        void findByCardNumber_shouldReturnCard() {
            // When
            Optional<Cards> result = cardsRepository.findByCardNumber(testCard.getCardNumber());

            // Then
            assertThat(result).isPresent();
            assertThat(result.get().getCardNumber()).isEqualTo(testCard.getCardNumber());
            assertThat(result.get().getMobileNumber()).isEqualTo(TEST_MOBILE_NUMBER);
        }

        @Test
        @DisplayName("Should return empty when card number does not exist")
        void findByCardNumber_shouldReturnEmpty_whenCardNotFound() {
            // When
            Optional<Cards> result = cardsRepository.findByCardNumber("0000000000000000");

            // Then
            assertThat(result).isEmpty();
        }
    }
}
