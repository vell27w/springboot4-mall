package com.vell27w.mall.common;

import lombok.Getter;

/**
 * 业务异常 —— Service 层发现「业务规则不满足」时抛这个。
 *
 * 【为什么继承 RuntimeException 而不是 Exception？】
 * 面试高频题「受检异常 vs 运行时异常」。
 * 继承 Exception（受检）的话，每个调用它的方法都得写 try-catch 或 throws，
 * 一路污染到 Controller。业务异常的特点是「调用方通常处理不了，
 * 只能告诉用户失败原因」，所以让它自动往上冒泡、由全局异常处理器统一接住最合适。
 *
 * 另一个后果：Spring 的 @Transactional 默认「遇到 RuntimeException 才回滚」。
 * 如果这里继承 Exception，抛出后事务不会回滚，库存扣了订单没生成 —— 数据就烂了。
 * 这条是「改坏它会怎样」的标准答案，面试问到事务一定要能说出来。
 *
 * 【它和 Result.fail() 的分工】
 *   Result.fail()      —— 在 Controller 层返回，适合「接口层面的失败」
 *   BusinessException  —— 在 Service 层抛出，适合「业务规则不满足」
 * 两者最终都会变成同一个 Result 结构返回给调用方（见 GlobalExceptionHandler），
 * 区别只是从哪一层表达更顺手。
 */
@Getter
public class BusinessException extends RuntimeException {

    private final Integer code;

    public BusinessException(ResultCode resultCode) {
        super(resultCode.getMsg());
        this.code = resultCode.getCode();
    }

    /** 错误码取枚举，但文案需要临时定制时用这个 */
    public BusinessException(ResultCode resultCode, String msg) {
        super(msg);
        this.code = resultCode.getCode();
    }

    public BusinessException(Integer code, String msg) {
        super(msg);
        this.code = code;
    }
}
