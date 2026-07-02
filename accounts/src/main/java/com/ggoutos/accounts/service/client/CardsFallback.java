package com.ggoutos.accounts.service.client;

import com.ggoutos.utils.dto.CardsDto;
import org.springframework.stereotype.Component;

@Component
public class CardsFallback implements CardsFeignClient {

    @Override
    public CardsDto fetchCardDetails(String correlationId, String mobileNumber) {
        CardsDto cardsDto = new CardsDto();
        cardsDto.setMobileNumber(mobileNumber);
        cardsDto.setServiceStatus("UNAVAILABLE");
        return cardsDto;
    }

}
