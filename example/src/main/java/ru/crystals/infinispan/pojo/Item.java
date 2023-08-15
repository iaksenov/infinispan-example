package ru.crystals.infinispan.pojo;

public class Item implements ItemInterface {

    private static final long serialVersionUID =  1L;

    private String item;

    private Long price;

    public Item() {
    }

    public Item(String item, Long price) {
        this.item = item;
        this.price = price;
    }

    public String getItem() {
        return item;
    }

    public void setItem(String item) {
        this.item = item;
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
        return "Items{" +
                "item='" + item + '\'' +
                ", price=" + price +
                '}';
    }

}
