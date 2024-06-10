package m.cmp.appManager;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients // 여기
public class AppManagerApplication {

    public static void main(String[] args) {
        SpringApplication.run(AppManagerApplication.class, args);
    }

}
