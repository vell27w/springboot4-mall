package com.vell27w.mall.common;

import lombok.Data;

/**
 * 统一返回体 —— 全项目所有接口的返回值都是这个结构。
 *
 * 【为什么要统一？】面试常问。
 * 1. 前端（或调用方）只需要写一套解析逻辑：先看 code，再取 data。
 *    如果没有统一结构，每个接口返回的 JSON 长得都不一样，调用方要写一堆 if。
 * 2. 错误能带上「业务错误码」，比 HTTP 状态码表达力强。
 *    比如「库存不足」和「用户未登录」都是 400/401 级别的失败，
 *    但业务上要区分，靠 code 区分。
 * 3. 统一结构才能配合「全局异常处理器」一起用 —— 见 GlobalExceptionHandler。
 *    有了它，Service 里抛异常就行，不用在每层手工拼错误返回。
 *
 * 【为什么 code 用 200 而不是 HTTP 200？】
 * 这里的 code 是「业务码」，和 HTTP 状态码是两回事。
 * 现在这个项目里两者恰好一致（成功都是 200），但它们是独立的：
 * HTTP 状态码由 web 容器/框架决定，业务码由业务决定。
 * 大厂里常见做法是 HTTP 一律返 200，业务结果全看 code —— 两种都行，别混着用。
 *
 * 【改坏它会怎样？】
 * 如果把静态工厂方法删掉、改成 public 构造器，那调用方就能随便 new 出一个
 * code=200 但 data=null 的对象。统一返回体的价值就在于「成功长什么样、
 * 失败长什么样」在代码里只有一处定义。构造器一旦公开，这个约束就散了。
 */
@Data
public class Result<T> {

    /** 业务状态码，取值见 {@link ResultCode} */
    private Integer code;

    /** 提示信息，失败时给人类看的原因 */
    private String msg;

    /** 业务数据。失败时通常是 null */
    private T data;

    /**
     * 构造器私有。
     *
     * 外部只能通过下面的静态方法创建 —— 这样就保证了
     * 「不可能造出一个 code 和 msg 对不上的 Result」。
     */
    private Result(Integer code, String msg, T data) {
        this.code = code;
        this.msg = msg;
        this.data = data;
    }

    /** 成功，带数据 */
    public static <T> Result<T> ok(T data) {
        return new Result<>(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMsg(), data);
    }

    /** 成功，无数据（删除、更新这类接口用） */
    public static <T> Result<T> ok() {
        return new Result<>(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMsg(), null);
    }

    /** 失败，用预定义错误码 */
    public static <T> Result<T> fail(ResultCode resultCode) {
        return new Result<>(resultCode.getCode(), resultCode.getMsg(), null);
    }

    /** 失败，自定义 code 和 msg（业务里临时需要时用） */
    public static <T> Result<T> fail(Integer code, String msg) {
        return new Result<>(code, msg, null);
    }
}
