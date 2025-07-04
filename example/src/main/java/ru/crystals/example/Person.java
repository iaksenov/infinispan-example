package ru.crystals.example;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Person implements Serializable {

    private static final long serialVersionUID =  1L;

    private String name;
    private String surname;
    private Long value;
    private List<ItemInterface> items;
    private String qq;
    private Long lastUpdate = System.currentTimeMillis();

    public Person(String name, String surname) {
        this.name = name;
        this.surname = surname;
    }

}