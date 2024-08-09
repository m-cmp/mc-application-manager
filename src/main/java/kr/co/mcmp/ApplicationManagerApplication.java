package kr.co.mcmp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import springfox.documentation.swagger2.annotations.EnableSwagger2;

import java.io.UnsupportedEncodingException;
import java.security.GeneralSecurityException;

@EnableSwagger2
@SpringBootApplication
public class ApplicationManagerApplication {

    public static void main(String[] args) throws GeneralSecurityException, UnsupportedEncodingException {
        System.out.println("========= boot start test string console =========");
        SpringApplication.run(ApplicationManagerApplication.class, args);
        System.out.println("========= boot start test string console =========");

    }


}
