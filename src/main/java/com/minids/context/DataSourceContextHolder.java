package com.minids.context;

/**
 * 数据源上下文持有器：基于 ThreadLocal 保存"当前线程要使用哪个数据源"的 key。
 * <p>
 * 命名拆解：DataSource（数据源）+ Context（上下文：当前请求/线程的环境）+ Holder（持有者）。
 * <p>
 * 为什么用 ThreadLocal 而不是 static 变量：
 *   高并发下多个线程共享一个变量会互相覆盖
 *   ThreadLocal 让每条线程拥有独立副本，互不可见。
 * <p>
 * 使用约定：谁 set 谁 clear —— 线程池中的线程会被复用，
 * 不 remove 会让下一个请求读到上一个请求残留的 key，导致查错库。
 */
public class DataSourceContextHolder {

    /**
     * 私有构造器：工具类不需要实例，禁止 new（所有方法都是 static，状态存在当前线程的 ThreadLocal 里）。
     * 如果构造器是 public（或默认），别人可以写出这种代码：
     *        DataSourceContextHolder holder = new DataSourceContextHolder();
     *        holder.set("slave");   ← 语法能过，但毫无意义：
     *                                 set 是 static 的，值存的是"当前线程的柜子"，
     *                                 跟这个 holder 对象一点关系都没有
     *      更糟的是：新手会以为"我 new 了就能有自己的上下文"，实际并没有
     */
    private DataSourceContextHolder(){}

    private static final ThreadLocal<String> context = new ThreadLocal<>();

    public static void set(String dsSet){
        context.set(dsSet);
    }

    public static String get(){
        return context.get();
    }

    public static void clear(){
        context.remove();
    }
}
