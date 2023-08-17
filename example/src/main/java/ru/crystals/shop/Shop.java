package ru.crystals.shop;

import lombok.Builder;
import lombok.Data;

import java.io.Serializable;

/**
 * Какая-то сущность магазина для кэша
 */
@Data
@Builder
public class Shop implements Serializable {

    private static final long serialVersionUID =  1L;

    private long id;
    private String name;
    private String address;
    private long cityId;
    private String cityName;
    private long regionId;
    private String regionName;
    private long formatId;
    private String formatName;

}
