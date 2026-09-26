package com.vell27w.mall.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 接口文档的全局信息（标题、版本、说明）。
 *
 * ============================================================
 * 【先搞清楚它在干什么 —— 这段比配置本身重要】
 * ============================================================
 * 接口文档**不是手写的**，是 springdoc 在**应用启动后、第一次被访问时**
 * 现算出来的。过程是：
 *
 *   1. springdoc 扫描所有 @RestController 的类
 *   2. 用**反射**读每个方法上的注解：@GetMapping 知道路径和 HTTP 方法，
 *      参数上的 @NotBlank 知道「这个字段必填」，
 *      返回类型 Result<GoodsListVO> 知道返回结构长什么样
 *   3. 把这些拼成一份 **OpenAPI 格式的 JSON**，挂在 /v3/api-docs
 *   4. Swagger UI 只是**渲染器** —— 它拉那份 JSON 画成网页
 *
 * 所以关键推论有两个：
 *
 *   - **接口没出现在文档里 → 不是 UI 的问题，是那份 JSON 里没有。**
 *     直接浏览器打开 /v3/api-docs 看原始 JSON，一眼就知道。
 *     不知道这条的人会一直在 UI 上刷新、清缓存，方向从一开始就错了。
 *   - **加了注解不用重启就能看到？** 因为它是运行时算的，不是编译期生成的。
 *     但也正因为如此，**注解写错了编译不会报错** —— 文档错了没人拦你。
 *
 * ============================================================
 * 【改坏它会怎样？】
 * ============================================================
 * 这个类只影响文档的「门面」（标题/版本），删掉不影响任何接口功能。
 * 但真正会坏事的是**配置层面**：
 *
 *   - 线上忘了关（application-prod.properties 里的 springdoc.*.enabled=false）
 *     → 接口清单、参数结构对全世界公开，等于免费送一份渗透测试说明书。
 *   - 文档和代码不一致才是最贵的成本：前端照着文档接了，
 *     结果字段名对不上，来回扯皮。所以**改了接口要顺手改注解**。
 */
@Configuration
public class OpenApiConfig {

    /**
     * 也可以不写这个类 —— springdoc 有默认值，界面照样能用。
     * 写它的唯一理由是让文档标题说人话（默认标题是 "OpenAPI definition"）。
     */
    @Bean
    public OpenAPI mallOpenApi() {
        return new OpenAPI().info(new Info()
                .title("mall 电商后端 API")
                .version("v1")
                .description("""
                        单体电商后端：商品 / 购物车 / 订单。

                        调用说明：
                        - 所有接口的返回都是统一结构 {code, msg, data}，code=200 表示成功
                        - 需要登录的接口在请求头带 X-Token
                        - 每个响应都带 X-Trace-Id 头，报错时把它一起贴出来方便定位
                        """));
    }
}
