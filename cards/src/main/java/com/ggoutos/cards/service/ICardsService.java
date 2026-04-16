package com.ggoutos.cards.service;

import com.ggoutos.utils.dto.CardsDto;

public interface ICardsService {


    /**
     * Create card based on mobile number
     * @param mobileNumber - Input Mobile Number
     */
    void createCard(String mobileNumber);


    /**
     * Fetch card details based on mobile number
     * @param mobileNumber - Input Mobile Number
     * @return Card Details based on a given mobileNumber
     */
    CardsDto fetchCard(String mobileNumber);

    /**
     * Fetch card details based on customer ID
     * @param customerId - Input Customer ID
     * @return Card Details based on a given customerId
     */
    CardsDto fetchCardByCustomerId(Long customerId);

    /**
     * Update card details
     * @param cardsDto - CardsDto Object
     * @return boolean indicating if the update of card details is successful or not
     */
    boolean updateCard(CardsDto cardsDto);

    /**
     * Delete card details
     * @param mobileNumber - Input Mobile Number
     * @return boolean indicating if the delete of card details is successful or not
     */
    boolean deleteCard(String mobileNumber);

}

