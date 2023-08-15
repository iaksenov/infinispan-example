package ru.crystals.infinispan.pojo;

import java.io.Serializable;
import java.util.List;

public class Person implements Serializable {

    private static final long serialVersionUID =  1L;

    private String name;

    private String surname;

    private Long value;

    private List<ItemInterface> items;

    private String qq;

    public Person(String name, String surname) {
        this.name = name;
        this.surname = surname;
    }

    public Person() {
        System.out.println("Person CONSTRUCTOR");
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSurname() {
        return surname;
    }

    public void setSurname(String surname) {
        this.surname = surname;
    }

    public Long getValue() {
        return value;
    }

    public void setValue(Long value) {
        this.value = value;
    }

    public Long getItemsSum() {
        if (items == null) {
            return 0L;
        } else {
            return items.stream().mapToLong(ItemInterface::getPrice).sum();
        }
    }

    public List<ItemInterface> getItems() {
        return items;
    }

    public void setItems(List<ItemInterface> items) {
        this.items = items;
    }

    public String getQq() {
        return qq;
    }

    public void setQq(String qq) {
        this.qq = qq;
    }


    @Override
    public String toString() {
        return "Person{" +
                "name='" + name + '\'' +
                ", surname='" + surname + '\'' +
                ", value=" + value +
                ", items=" + items +
                ", qq='" + qq + '\'' +
                '}';
    }
}