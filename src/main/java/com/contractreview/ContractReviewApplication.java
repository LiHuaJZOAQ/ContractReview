package com.contractreview;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
@MapperScan("com.contractreview.mapper")
public class ContractReviewApplication {
    public static void main(String[] args) {
        SpringApplication.run(ContractReviewApplication.class, args);
    }
}
