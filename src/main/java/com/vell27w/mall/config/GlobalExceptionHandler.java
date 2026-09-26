package com.vell27w.mall.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.vell27w.mall.common.BusinessException;
import com.vell27w.mall.common.Result;
import com.vell27w.mall.common.ResultCode;

/**
 * 全局异常处理器 —— 整个应用「所有没被接住的异常」最后都落到这里。
 *
 * 【它解决什么问题？】
 * 没有它的话，Service 里抛一个异常，用户看到的是一整页 Java 堆栈
 * （Spring 默认的 /error 页面）。这既难看，又把内部类名、SQL、包结构
 * 泄露给了外部 —— 属于信息泄露。
 * 有了它，任何异常都会被转成统一的 Result 结构返回。
 *
 * 【@RestControllerAdvice 是什么？】
 * 面试常问「AOP 在 Spring 里有哪些应用」的标准答案之一。
 * 它是 @ControllerAdvice + @ResponseBody 的组合，
 * 底层正是 Spring AOP：给所有 Controller 织入「异常处理」这层逻辑，
 * 所以 Controller 里一行 try-catch 都不用写。
 *
 * 【处理器的匹配顺序 —— 这条是改坏它最典型的后果】
 * Spring 会挑「最具体」的那个 @ExceptionHandler。
 * 如果这里只留一个 Exception.class 的处理器，那 BusinessException
 * 也会被它接住，结果就是「业务异常返回 500 + 通用文案」，
 * 前端拿不到真正的错误码。所以具体类型必须排在通用类型之前处理 ——
 * 这里靠的是 Spring 自己的匹配算法（精确类型优先），不是靠代码顺序。
 *
 * 【为什么要 log.error 而不是 log.info？】
 * 未知异常（最后一个处理器）说明是代码 bug，必须能在日志里告警到。
 * 业务异常是「预期内的失败」（用户没登录、库存不足），不该刷 error 日志。
 * 两者混在一起的表现是：日志里全是 error，真正的 bug 淹没了。
 *
 * ============================================================
 * 【HTTP 状态码到底该不该跟业务码一致？—— 本项目的答案】
 * ============================================================
 * 先看清楚现在的行为：商品不存在时，**HTTP 状态码是 200，body 里 code=404**。
 * 也就是说 HTTP 状态码只表示「服务器收到并处理了这个请求」，
 * 具体的成败由 body 里的 code 表达。这是国内主流做法，
 * 也是参考项目的做法（前端只认 body.code，不解析 HTTP 状态码）。
 *
 * 但「全部 200」有个真实代价：**监控看不到错误率**。
 * 运维一般靠统计 HTTP 5xx 的比例来告警，如果所有错误都藏在 200 里，
 * 服务挂了一半也不会响警报。
 *
 * 所以本项目用一条规则把它分开：
 *
 *   ┌──────────────────────────────────────────────────────┐
 *   │ 客户端自己能改好的错误 → HTTP 200 + body 里的业务码 │
 *   │ 客户端无能为力的故障   → 真正的 HTTP 5xx             │
 *   └──────────────────────────────────────────────────────┘
 *
 * 按这条规则套一遍：
 *   - 商品不存在    → 用户换个 id 就行      → 200 + code=404
 *   - 参数校验失败  → 用户改参数就行        → 200 + code=400
 *   - 库存不足      → 用户减数量就行        → 200 + code=xxxx
 *   - 代码 NPE / 数据库连不上 → 用户做什么都没用 → HTTP 500
 *
 * 面试问「你们的 HTTP 状态码怎么设计的」，这样答就是完整答案 ——
 * 而不是「我们都是 200」或者「我们都是对的」。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 业务异常 —— Service 层主动抛的
     */
    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusinessException(BusinessException e) {
        log.warn("业务异常: code={}, msg={}", e.getCode(), e.getMessage());
        return Result.fail(e.getCode(), e.getMessage());
    }

    /**
     * @RequestBody 上的 @Valid 校验失败
     *
     * 触发场景：请求体是 JSON，字段上写了 @NotNull/@NotBlank 之类但不满足。
     * 这种情况下 Spring 抛的是 MethodArgumentNotValidException。
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleMethodArgumentNotValid(MethodArgumentNotValidException e) {
        String msg = firstFieldError(e.getBindingResult().getFieldErrors());
        log.warn("参数校验失败: {}", msg);
        return Result.fail(ResultCode.VALIDATE_FAILED.getCode(), msg);
    }

    /**
     * 表单/查询参数绑定校验失败
     *
     * 和上面那个的区别：这个用于非 JSON 请求体（比如 @ModelAttribute 表单、查询参数）。
     * 注意：Spring Boot 3 之后这两个异常已经合并到一个父类了，
     * 但为了兼容各种触发路径，两个都留着更稳。
     */
    @ExceptionHandler(BindException.class)
    public Result<Void> handleBindException(BindException e) {
        String msg = firstFieldError(e.getBindingResult().getFieldErrors());
        log.warn("参数绑定失败: {}", msg);
        return Result.fail(ResultCode.VALIDATE_FAILED.getCode(), msg);
    }

    /**
     * 请求体根本不是合法 JSON（或者类型对不上）
     *
     * 【这个处理器是怎么被发现该加的？】
     * 实测时故意发了一段畸形 JSON：
     *     {"name":"x",,,"price":
     * 结果返回的是 **HTTP 500 + "操作失败"**。
     *
     * 这不对，而且是两层不对：
     *   1. 语义错了 —— 客户端发了坏数据，这是**客户端错误**（4xx），
     *      不是服务端故障（5xx）。前端拿到 500 会以为是后端挂了，
     *      跑去查后端日志，白折腾。
     *   2. 它污染了监控 —— 上面刚定的规则是「靠 5xx 比例告警」。
     *      现在每个畸形请求都会推高 5xx，真出故障时警报反而不显眼。
     *
     * 不加这个处理器，它就会掉进最后的 Exception 兜底里。
     *
     * 【为什么单独处理，而不是让 MessageArgumentNotValid 那个接？】
     * 因为这是两个不同阶段的事：
     *   - HttpMessageNotReadableException：JSON **还没解析成对象**就失败了
     *   - MethodArgumentNotValidException：对象建好了，**字段值不合法**
     * 前者连字段名都取不到，所以只能给个笼统文案。
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Result<Void> handleNotReadable(HttpMessageNotReadableException e) {
        // 只记第一行，别把整个堆栈刷进日志 —— 这类错误是用户输入问题，不是 bug
        log.warn("请求体解析失败: {}", e.getMessage());
        return Result.fail(ResultCode.VALIDATE_FAILED.getCode(), "请求体格式错误，不是合法的 JSON");
    }

    /**
     * 方法参数上的校验失败（@PathVariable / @RequestParam 上写 @Valid）
     *
     * 【为什么以前没见过这个异常？】
     * Spring 6.1 之前，@Valid 写在 @PathVariable 上是**不生效**的 ——
     * 只在 @RequestBody 上生效。6.1 之后新增了方法级校验，
     * 于是多出这个 HandlerMethodValidationException。
     * 本项目给查询参数加校验时会真的撞上它。
     *
     * 它是 ResponseStatusException 的子类，默认自带 400，
     * 但被 @RestControllerAdvice 接住后状态码由我们决定 ——
     * 按类注释的规则，这是客户端能改的，所以走 200 + 业务码。
     */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public Result<Void> handleHandlerMethodValidation(HandlerMethodValidationException e) {
        String msg = e.getParameterValidationResults().stream()
                .flatMap(r -> r.getResolvableErrors().stream())
                .map(MessageSourceResolvable::getDefaultMessage)
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElse(ResultCode.VALIDATE_FAILED.getMsg());
        log.warn("方法参数校验失败: {}", msg);
        return Result.fail(ResultCode.VALIDATE_FAILED.getCode(), msg);
    }

    /**
     * 路径根本不存在 —— 没有任何 @RequestMapping 能匹配上这个 URL
     *
     * ============================================================
     * 【不加这个处理器会发生什么 —— 这是删掉 Product 接口时撞出来的真问题】
     * ============================================================
     * 删掉 ProductController 之后，访问 /products/1 得到的是：
     *      HTTP 500  +  {"code":500,"msg":"操作失败"}
     * 日志里还有一行 `ERROR 系统异常` 加完整堆栈。
     *
     * 三处都不对：
     *
     * ① 语义错了。URL 写错了是**客户端**的问题，返回 5xx 等于说「我们服务器挂了」。
     *    前端看到 500 会去翻后端日志，其实他只需要改一个字母。
     * ② **它污染了 5xx 告警信号** —— 和上面处理畸形 JSON 是完全同一类问题。
     *    网上爬虫天天在扫 /wp-login.php、/.env、/admin 这些不存在的路径，
     *    每扫一次就产生一条 ERROR + 一次 5xx。真出故障时，告警全被淹了。
     * ③ 日志级别错了。请求一个不存在的路径不是 bug，不该打 ERROR + 堆栈。
     *
     * ============================================================
     * 【为什么这里给真 HTTP 404，而 /goods/1013 那种却给 HTTP 200 + code 404？】
     * ============================================================
     * 这两个 404 是**两个层次**的事，别混：
     *
     *   /goods/1013 —— 接口**存在**。请求被正常处理完了，
     *                  只是业务上那个商品不可见。业务结论写在 body 里，
     *                  HTTP 状态码依然是 200（请求本身成功了）。
     *
     *   /products/1 —— 接口**不存在**。服务器压根没听懂这个请求，
     *                  连「该执行哪段代码」都没定下来 ——
     *                  这是**传输层**的事实，该由 HTTP 状态码表达。
     *
     * 一句话：**HTTP 状态码答「服务器有没有处理这个请求」，
     * body 里的 code 答「业务结果是什么」。**
     * 两者都不返回 5xx，所以都不会污染告警 —— 这才是判据。
     *
     * ⚠️ NoResourceFoundException 是 Boot 3.2 之后才有的
     * （之前叫 NoHandlerFoundException，而且默认还要手动开开关才抛）。
     * 找不到它的人会一直以为 404 是 404，其实掉进了兜底 500。
     */
    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Result<Void> handleNoResourceFound(NoResourceFoundException e) {
        // 只记 WARN 打一行，不打堆栈 —— 这绝大多数是爬虫或拼错的 URL，不是 bug
        log.warn("请求了不存在的路径: {}", e.getResourcePath());
        return Result.fail(ResultCode.NOT_FOUND.getCode(), "接口不存在，请检查 URL");
    }

    /**
     * 兜底 —— 任何没被上面接住的异常
     *
     * 返回笼统文案，不把异常细节透给外部；
     * 但完整堆栈要打进日志，否则线上出问题无从查起。
     *
     * 【为什么只有这里加了 @ResponseStatus，上面那几个没加？】
     * 见类注释的规则：走到这里说明是**代码 bug 或基础设施故障**，
     * 客户端做什么都改变不了结果，属于「服务端故障」。
     * 让它返回真正的 HTTP 500，监控才能统计到错误率、才能告警。
     *
     * 如果这里也不加，会出现最坑的一种情况：
     * 数据库挂了，所有接口仍然返回 HTTP 200，
     * 运维看监控一片绿，用户那边全在报错。
     *
     * ⚠️ 前端要注意：这种情况下 HTTP 客户端（axios 等）会**抛异常**，
     * 而不是走正常的 then 分支。这是有意的 —— 500 本来就该按异常处理。
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<Void> handleException(Exception e) {
        log.error("系统异常", e);
        return Result.fail(ResultCode.FAILED);
    }

    /**
     * 取第一条字段校验错误的信息。
     * 校验可能同时有多个字段不合法，这里只报第一条 ——
     * 一次只让用户改一个地方，比一次抛一堆更好用。
     */
    private String firstFieldError(java.util.List<FieldError> fieldErrors) {
        if (fieldErrors == null || fieldErrors.isEmpty()) {
            return ResultCode.VALIDATE_FAILED.getMsg();
        }
        FieldError first = fieldErrors.get(0);
        return first.getField() + ": " + first.getDefaultMessage();
    }
}
