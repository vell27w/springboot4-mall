package com.vell27w.mall;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 启动类。
 *
 * @SpringBootApplication 是个三合一注解，拆开看是：
 *   @SpringBootConfiguration —— 声明这是个配置类
 *   @EnableAutoConfiguration —— 打开自动配置（Spring Boot 的核心魔法）
 *   @ComponentScan           —— 扫描「本类所在包及其子包」下的 @Component/@Service/@RestController
 *
 * 所以这个类必须放在所有业务代码的「最外层包」，否则扫描不到。
 * 这也是为什么包名一旦定了就别乱挪 —— 挪错了表现是「启动成功但 Bean 找不到」，
 * 报错信息是 NoSuchBeanDefinitionException，新手很难第一眼看出是包路径问题。
 */
@SpringBootApplication
public class MallApplication {

    public static void main(String[] args) {
        SpringApplication.run(MallApplication.class, args);
    }
}
