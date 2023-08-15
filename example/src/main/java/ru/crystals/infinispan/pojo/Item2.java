package ru.crystals.infinispan.pojo;

public class Item2 implements ItemInterface {

    private static final long serialVersionUID =  1L;

    private Long price;

    public Item2() {
    }

    @Override
    public Long getPrice() {
        return price;
    }

    public void setPrice(Long price) {
        this.price = price;
    }

    @Override
    public String toString() {
        return "Item2{" +
                "price=" + price +
                '}';
    }
}
