package com.vell27w.mall.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * 给每个请求打一个 traceId，串起这次请求打的所有日志。
 *
 * ============================================================
 * 【它解决什么问题？】
 * ============================================================
 * 线上出问题时，日志是几万行混在一起、好几十个请求交错打出来的。
 * 用户说「我下单失败了」，你拿到一个时间点，然后要在日志里找：
 * 这次请求进的是哪个 Controller？扣库存扣成功没有？
 * 抛异常的那行和前面那些行是不是同一次请求？
 *
 * 没有 traceId 就只能靠 thread 名字和时间戳猜，多线程下必然猜错。
 *
 * 有了它，搜索框里贴一个 id，这次请求从头到尾经过的每一行都在眼前。
 * 前端报错时把 traceId 一起报上来，排查就是从「大海捞针」变成「按号取件」。
 *
 * ============================================================
 * 【怎么实现的？三个东西配合】
 * ============================================================
 *   1. MDC      —— SLF4J 提供的一个 ThreadLocal<Map<String,String>>。
 *                  本质就是「当前线程随身携带的一个小字典」。
 *   2. 日志格式 —— application.properties 里的 %X{traceId}，
 *                  意思是「从当前线程的 MDC 里取 traceId 这个 key」。
 *   3. 这个过滤器 —— 请求进来时往 MDC 放，请求结束时把 MDC 清掉。
 *
 * 注意这里**没有任何分布式追踪框架**（Micrometer Tracing / Brave / OTel）。
 * 单机排查不需要它们 —— 它们要解决的是「跨服务传递」，
 * 而 MDC 是 JVM 内的，出了这个进程就没了。
 * 真要跨服务，靠的是把 traceId 放进 HTTP 头传下去（本类的 incoming 分支就是干这个的）。
 *
 * ============================================================
 * 【改坏它会怎样？】
 * ============================================================
 * 最容易踩的一处：**finally 里删掉 MDC.clear()。**
 *
 * 后果不是「日志里少个字段」这么轻。Tomcat 处理请求用的是**线程池** ——
 * 线程用完不销毁，下一个请求接着用。MDC 是挂在线程上的，
 * 你不清，它就跟着线程活到下一个请求去。
 *
 * 于是会出现：用户 B 的日志里打着用户 A 的 traceId。
 * 搜索一个 traceId，搜出来的是两次不相干的请求 ——
 * 这比没有 traceId 更糟，因为**它让你相信了一个错误的结论**。
 *
 * 这个「线程池 + ThreadLocal = 必须清理」的组合，是 Java 面试的高频考点，
 * 同一个道理也适用于 ThreadLocal、SimpleDateFormat、事务上下文。
 */
@Component
// 提到最高优先级：要在其它过滤器（尤其是 Spring Security 那批）之前跑，
// 否则它们在 MDC 还是空的时候就打日志了。
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

    /** MDC 里的 key，必须和 application.properties 里 %X{traceId} 的拼写一致 */
    public static final String TRACE_ID = "traceId";

    /**
     * 上游传 traceId 用的请求头名。
     *
     * 【为什么要有「上游传进来的」这一支？】
     * 单体项目里永远用不到，但网关 + 多个服务的架构里是必须的：
     * 网关生成一个 traceId，往下游每个服务的请求头里塞，
     * 这样一次用户请求跨了 5 个服务，5 个服务的日志能拼成一条线。
     * 名字用 X-Trace-Id 是社区习惯（W3C 标准叫 traceparent，格式更复杂）。
     */
    public static final String TRACE_ID_HEADER = "X-Trace-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String traceId = resolveTraceId(request);
        try {
            MDC.put(TRACE_ID, traceId);
            // 回写响应头：前端报错时能把它一起带上，也可以直接在浏览器 F12 里看到
            response.setHeader(TRACE_ID_HEADER, traceId);

            // 这一行之后，本次请求在 Controller / Service / Mapper / 异常处理器
            // 里打的每一条日志，都会自动带上这个 traceId。
            filterChain.doFilter(request, response);
        } finally {
            // 【这一行不能删】见类注释「改坏它会怎样」
            MDC.remove(TRACE_ID);
        }
    }

    /**
     * 有上游传来的就用上游的，没有就自己生成。
     *
     * ⚠️ 这里没做格式校验。生产环境要检查上游值是否合法 ——
     * 因为它是**外部输入**，用户可以在请求头里塞任意字符串，
     * 塞个换行符就能伪造日志行（日志注入）。
     * 本例是练手项目，先按「能跑通」写，知道这个缺口在哪就行。
     */
    private String resolveTraceId(HttpServletRequest request) {
        String incoming = request.getHeader(TRACE_ID_HEADER);
        if (incoming != null && !incoming.isBlank()) {
            return incoming;
        }
        // 32 位十六进制的 UUID 去掉横线，取前 16 位。
        // 【为什么不全取 32 位？】日志一行本来就长，16 位的碰撞概率
        // （同一次排查窗口内撞号）已经低到可以忽略，短一点更好读。
        // 真按 W3C 标准应该是 32 位，这里有取舍。
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }
}
