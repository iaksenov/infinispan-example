package ru.crystals.shop;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Какая-то сущность магазина для кэша
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
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

    private Long lastUpdated = System.currentTimeMillis();

}
