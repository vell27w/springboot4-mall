package com.vell27w.mall.common;

import lombok.Data;

/**
 * 分页查询入参基类 —— 所有带分页的查询参数都继承它。
 *
 * 【为什么要有「基类」而不是每个 Param 各写一遍？】
 * pageNum / pageSize 这两个字段每个分页接口都有。
 * 各写一遍的话，「默认值是多少、最大允许多少」这个规则就有 N 份拷贝，
 * 改一处漏一处。
 *
 * 【为什么在这里做上限保护？】
 * pageSize 不设上限是真实的线上事故来源：有人传 pageSize=100000，
 * 数据库要一次性捞出十万行，内存直接爆。
 * 这不是理论风险 —— 「接口被人用错参数打死」是最常见的可用性事故之一。
 * 所以这里把上限卡死在 MAX_PAGE_SIZE。
 *
 * 【改坏它会怎样？】
 * 把 pageSize 的上限去掉、或者不设默认值（前端不传时是 null），
 * 立刻会出两类问题：
 *   1. null 传到 SQL 的 limit 里 → 报 SQL 语法错
 *   2. 超大 pageSize → 慢查询拖垮数据库
 * 所以 setter 里做了保护，而不是指望调用方自觉。
 */
@Data
public class PageQuery {

    /** 每页最大条数，超出按这个值截断 */
    private static final int MAX_PAGE_SIZE = 100;

    /** 默认页码 */
    private static final int DEFAULT_PAGE_NUM = 1;

    /** 默认每页条数 */
    private static final int DEFAULT_PAGE_SIZE = 10;

    /** 页码，从 1 开始 */
    private Integer pageNum = DEFAULT_PAGE_NUM;

    /** 每页条数 */
    private Integer pageSize = DEFAULT_PAGE_SIZE;

    /**
     * 注意这两个 setter 是手写的，覆盖了 Lombok @Data 生成的版本。
     * Lombok 会跳过已经存在的方法，所以这里生效。
     */
    public void setPageNum(Integer pageNum) {
        this.pageNum = (pageNum == null || pageNum < 1) ? DEFAULT_PAGE_NUM : pageNum;
    }

    public void setPageSize(Integer pageSize) {
        if (pageSize == null || pageSize < 1) {
            this.pageSize = DEFAULT_PAGE_SIZE;
        } else {
            this.pageSize = Math.min(pageSize, MAX_PAGE_SIZE);
        }
    }

    /**
     * SQL 的 limit 偏移量。
     *
     * 页码从 1 开始（给人看），偏移量从 0 开始（给数据库用），
     * 所以第 1 页的偏移量是 0，第 2 页是 pageSize。
     * 这个换算忘了减 1 就会「永远跳过第一页」。
     */
    public int getOffset() {
        return (pageNum - 1) * pageSize;
    }
}
