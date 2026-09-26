package com.vell27w.mall;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 冒烟测试 —— 只验证「Spring 容器能起来」。
 *
 * 别小看这一个空测试。它能抓到的问题包括：
 *   - Bean 循环依赖
 *   - Mapper XML 写错导致 MyBatis 初始化失败
 *   - 配置文件语法错误、数据源连不上
 *
 * 【为什么值得留着？】
 * 上面这几类问题，症状都是「启动挂掉」。手工启动也能发现，
 * 但那是人肉重复劳动 —— 而且改完之后往往懒得再启一次。
 * 有了它，`mvn test` 是改完代码后成本最低的一道网。
 *
 * ⚠️ 注意：这个测试需要本地 MySQL 已启动且 shop 库存在，
 * 因为它会真的去连数据源。CI 环境要么配数据库，要么把它排除。
 */
@SpringBootTest
class MallApplicationTests {

    @Test
    void contextLoads() {
        // 空方法体是故意的：如果上下文加载失败，测试框架会在这里报错
    }
}
