package com.cmc.meeting.web;

import io.github.cdimascio.dotenv.Dotenv;
import org.mybatis.spring.annotation.MapperScan; // <-- 1. Import mới
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan; // <-- 2. Import mới
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories; // <-- 3. Import mới
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication

// --- CÁC ANNOTATION BỔ SUNG ---

// 1. Quét @Service, @Component, @Repository (các Adapter)
@ComponentScan(basePackages = "com.cmc.meeting") 

// 2. Chỉ cho Spring biết nơi tìm @Entity (trong module infrastructure)
@EntityScan(basePackages = "com.cmc.meeting.infrastructure.persistence.jpa.entity") 

// 3. Kích hoạt & chỉ cho Spring nơi tìm JpaRepository (trong module infrastructure)
@EnableJpaRepositories(basePackages = "com.cmc.meeting.infrastructure.persistence.jpa.repository")

// 4. (Bonus) Chỉ cho Spring nơi tìm MyBatis Mappers (để hết lỗi WARN)
@MapperScan(basePackages = "com.cmc.meeting.infrastructure.persistence.mybatis.mapper")
@EnableRabbit
@EnableAsync
@EnableScheduling
@EnableCaching
public class MeetingRoomApplication {

    public static void main(String[] args) {
        
        Dotenv dotenv = Dotenv.load();
        dotenv.entries().forEach(entry -> {
            System.setProperty(entry.getKey(), entry.getValue());
        });

        SpringApplication.run(MeetingRoomApplication.class, args);
    }
}