package kr.co.strato;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import springfox.documentation.swagger2.annotations.EnableSwagger2;

import java.io.UnsupportedEncodingException;
import java.security.GeneralSecurityException;
import java.util.List;

@EnableSwagger2
@SpringBootApplication
public class WorkflowManagerApplication {

    public static void main(String[] args) throws GeneralSecurityException, UnsupportedEncodingException {
        System.out.println("========= boot start test string console =========");
        SpringApplication.run(WorkflowManagerApplication.class, args);
        System.out.println("========= boot start test string console =========");

    }


}
