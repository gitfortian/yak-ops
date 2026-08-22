package io.yak.ops.boot;

import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Yak Ops application entry point.
 *
 * <p>Yak Ops modules are scanned from their shared root package. Yak Framework integrations are
 * loaded through their Spring Boot auto-configuration metadata.</p>
 */
@MapperScan(basePackages = "io.yak.ops", annotationClass = Mapper.class)
@SpringBootApplication(scanBasePackages = "io.yak.ops")
public class YakOpsApplication {

  public static void main(String[] args) {
    SpringApplication.run(YakOpsApplication.class, args);
  }
}
