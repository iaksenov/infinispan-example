package ru.crystals.infinispan;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class Application {

    public static void main(String[] args) {
//        long millis = TimeUnit.SECONDS.toMillis(40 + new Random().nextInt(180));
//        ExecutorService executorService = Executors.newSingleThreadExecutor();
//        executorService.submit(() -> {
//            try {
//                Thread.sleep(millis);
//            } catch (InterruptedException e) {
//                throw new RuntimeException(e);
//            }
//            System.exit(0);
//        });
        SpringApplication.run(Application.class, args);
    }

}
