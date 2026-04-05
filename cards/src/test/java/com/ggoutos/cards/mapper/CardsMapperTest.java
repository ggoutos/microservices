package com.ggoutos.cards.mapper;

import com.ggoutos.cards.entity.Cards;
import com.ggoutos.utils.dto.CardsDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CardsMapper Tests")
class CardsMapperTest {

    @Nested
    @DisplayName("mapToCardsDto() Tests")
    class MapToCardsDtoTests {

        @Test
        @DisplayName("Should map Cards entity to CardsDto")
        void mapToCardsDto_shouldMapCorrectly() {
            // Given
            Cards cards = new Cards();
            cards.setMobileNumber("9939321212");
            cards.setCardNumber("4532123456789012");
            cards.setCardType("Credit Card");
            cards.setTotalLimit(100000);
            cards.setAmountUsed(10000);
            cards.setAvailableAmount(90000);

            CardsDto cardsDto = new CardsDto();

            // When
            CardsDto result = CardsMapper.mapToCardsDto(cards, cardsDto);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getMobileNumber()).isEqualTo("9939321212");
            assertThat(result.getCardNumber()).isEqualTo("4532123456789012");
            assertThat(result.getCardType()).isEqualTo("Credit Card");
            assertThat(result.getTotalLimit()).isEqualTo(100000);
            assertThat(result.getAmountUsed()).isEqualTo(10000);
            assertThat(result.getAvailableAmount()).isEqualTo(90000);
        }
    }

    @Nested
    @DisplayName("mapToCards() Tests")
    class MapToCardsTests {

        @Test
        @DisplayName("Should map CardsDto to Cards entity")
        void mapToCards_shouldMapCorrectly() {
            // Given
            CardsDto cardsDto = new CardsDto();
            cardsDto.setMobileNumber("9939321212");
            cardsDto.setCardNumber("4532123456789012");
            cardsDto.setCardType("Debit Card");
            cardsDto.setTotalLimit(50000);
            cardsDto.setAmountUsed(5000);
            cardsDto.setAvailableAmount(45000);

            Cards cards = new Cards();

            // When
            Cards result = CardsMapper.mapToCards(cardsDto, cards);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getMobileNumber()).isEqualTo("9939321212");
            assertThat(result.getCardNumber()).isEqualTo("4532123456789012");
            assertThat(result.getCardType()).isEqualTo("Debit Card");
            assertThat(result.getTotalLimit()).isEqualTo(50000);
            assertThat(result.getAmountUsed()).isEqualTo(5000);
            assertThat(result.getAvailableAmount()).isEqualTo(45000);
        }
    }
}
