package com.ggoutos.cards.service.impl;

import com.ggoutos.cards.constants.CardsConstants;
import com.ggoutos.cards.entity.Cards;
import com.ggoutos.cards.exception.CardAlreadyExistsException;
import com.ggoutos.cards.exception.ResourceNotFoundException;
import com.ggoutos.cards.mapper.CardsMapper;
import com.ggoutos.cards.repository.CardsRepository;
import com.ggoutos.cards.service.ICardsService;
import com.ggoutos.utils.dto.CardsDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.Random;

@Service
@RequiredArgsConstructor
@Slf4j
public class CardsServiceImpl implements ICardsService {

    private final CardsRepository cardsRepository;

    /**
     * @param mobileNumber - Mobile Number of the Customer
     */
    @Override
    public void createCard(String mobileNumber) {
        Optional<Cards> optionalCards= cardsRepository.findByMobileNumber(mobileNumber);
        if(optionalCards.isPresent()){
            throw new CardAlreadyExistsException("Card already registered with given mobileNumber "+mobileNumber);
        }
        cardsRepository.save(createNewCard(mobileNumber));
    }

    /**
     * @param mobileNumber - Mobile Number of the Customer
     * @return the new card details
     */
    private Cards createNewCard(String mobileNumber) {
        Cards newCard = new Cards();
        newCard.setCardNumber(generateCardNumber());
        newCard.setMobileNumber(mobileNumber);
        newCard.setCardType(CardsConstants.CREDIT_CARD);
        newCard.setTotalLimit(CardsConstants.NEW_CARD_LIMIT);
        newCard.setAmountUsed(0);
        newCard.setAvailableAmount(CardsConstants.NEW_CARD_LIMIT);
        log.info("Card created successfully for mobile number: {}", mobileNumber);
        return newCard;
    }

    @Override
    public CardsDto fetchCard(String mobileNumber) {
        Cards cards = cardsRepository.findByMobileNumber(mobileNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Card", "mobileNumber", mobileNumber));
        return CardsMapper.mapToCardsDto(cards, new CardsDto());
    }

    @Override
    public boolean updateCard(CardsDto cardsDto) {
        Cards cards = cardsRepository.findByCardNumber(cardsDto.getCardNumber())
                .orElseThrow(() -> new ResourceNotFoundException("Card", "cardNumber", cardsDto.getCardNumber()));

        CardsMapper.mapToCards(cardsDto, cards);
        cardsRepository.save(cards);
        log.info("Card updated successfully for card number: {}", cardsDto.getCardNumber());
        return true;
    }

    @Override
    public boolean deleteCard(String mobileNumber) {
        Cards cards = cardsRepository.findByMobileNumber(mobileNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Card", "mobileNumber", mobileNumber));

        cardsRepository.deleteById(cards.getCardId());
        log.info("Card deleted successfully for mobile number: {}", mobileNumber);
        return true;
    }

    /**
     * Generate a random 16-digit card number
     *
     * @return generated card number
     */
    private String generateCardNumber() {
        Random random = new Random();
        long cardNumber = 1000000000000000L + random.nextLong(9000000000000000L);
        return String.valueOf(cardNumber);
    }

}

