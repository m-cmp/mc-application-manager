package kr.co.mcmp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

import java.io.UnsupportedEncodingException;
import java.security.GeneralSecurityException;

@SpringBootApplication
@EnableFeignClients
public class ApplicationManagerApplication {

    public static void main(String[] args) throws GeneralSecurityException, UnsupportedEncodingException {
        SpringApplication.run(ApplicationManagerApplication.class, args);
    }


}
