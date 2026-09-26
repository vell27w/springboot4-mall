package com.vell27w.mall.common;

import lombok.Data;

import java.util.List;

/**
 * 分页返回结构 —— 商品列表这类「一次返回不完」的接口用它包一层。
 *
 * 【为什么分页返回体和普通返回体要分开？】
 * 列表接口的调用方需要知道两件事：这一页的数据、以及总共有多少条。
 * 只有 List 的话，前端就没法渲染分页器（不知道总页数）。
 *
 * 【totalPages 是自己算的还是查出来的？】
 * 自己算的：total / pageSize 向上取整。
 * 数据库查出来的是 total（总条数），页数是纯计算。
 *
 * 【改坏它会怎样？】
 * 如果 totalPages 用整除（不向上取整），21 条数据、每页 10 条时，
 * 算出来是 2 页 —— 第 21 条永远翻不到。这属于「边界条件 bug」，
 * 数据量小的时候测不出来，上线后数据涨了才开始丢数据。
 */
@Data
public class PageResult<T> {

    /** 总条数（数据库里符合条件的总数，不是本页条数） */
    private long total;

    /** 当前页码，从 1 开始 */
    private int pageNum;

    /** 每页条数 */
    private int pageSize;

    /** 总页数 */
    private int totalPages;

    /** 本页数据 */
    private List<T> list;

    public PageResult(long total, int pageNum, int pageSize, List<T> list) {
        this.total = total;
        this.pageNum = pageNum;
        this.pageSize = pageSize;
        this.list = list;
        this.totalPages = (int) ((total + pageSize - 1) / pageSize);
    }
}
