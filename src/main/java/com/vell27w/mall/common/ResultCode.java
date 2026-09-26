package com.vell27w.mall.common;

import lombok.Getter;

/**
 * 错误码枚举 —— 全项目「有哪些失败情况」的唯一清单。
 *
 * 【为什么用枚举而不是散落的魔法数字？】
 * 面试常问的「魔法值」问题。如果全项目到处写 fail(5001, "库存不足")，
 * 那「库存不足」这个码到底有几个含义、有没有重复、改了哪里会受影响，
 * 没人说得清。收成枚举之后：
 *   1. 新增错误码必须来这里加，天然是一次集中决策
 *   2. 前端拿到 5001 能直接查到含义
 *   3. 改文案只改一处
 *
 * 【改坏它会怎样？】
 * 如果两个不同的业务含义用了同一个 code（比如「库存不足」和「商品已下架」都写 5001），
 * 前端就没法根据 code 区分该提示什么。这种 bug 在测试环境往往发现不了，
 * 因为文案是后端返回的、看着是对的 —— 只有前端想按 code 做分支时才会暴露。
 */
@Getter
public enum ResultCode {

    SUCCESS(200, "操作成功"),
    FAILED(500, "操作失败"),
    VALIDATE_FAILED(400, "参数校验失败"),
    UNAUTHORIZED(401, "未登录或登录已过期"),
    FORBIDDEN(403, "没有权限"),
    NOT_FOUND(404, "资源不存在");

    private final Integer code;
    private final String msg;

    ResultCode(Integer code, String msg) {
        this.code = code;
        this.msg = msg;
    }
}
