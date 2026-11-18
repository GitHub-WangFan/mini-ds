package com.minids;

/**
 * ThreadLocal 反面实验：用共享的 static 变量存值。
 * <p>
 * 现象：线程 A 存值后 sleep 100ms，这期间线程 B 把同一个 static 变量覆盖了，
 * 于是线程 A 醒来取到的是"线程B的值"——这就是 YGMES 里 ShortConnUtil 的真实 bug。
 * <p>
 * 对比实验：把下面 ThreadLocal 版本的代码放开、注释掉 static String 版本，
 * 再跑一次，两个线程各自取到自己的值（互不干扰）。
 */
public class ThreadLocalDemo {

//    static ThreadLocal<String> context = new ThreadLocal<>();

    static String context;

    public static void main(String[] args) {
        Thread threadA = new Thread(() -> {
//            context.set("存入线程A的值");
            context = "存入线程A的值";
            try {
                Thread.sleep(100);   // 故意等待，让线程B有时间覆盖值
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }

            System.out.println("线程A取到的值：" + context);
//            System.out.println("线程A取到的值：" + context.get());
        });

        Thread threadB = new Thread(() -> {
            context = "存入线程B的值";
//            context.set("存入线程B的值");

            System.out.println("线程B取到的值：" + context);
//            System.out.println("线程B取到的值：" + context.get());
        });

        threadA.start();
        threadB.start();
    }
}
