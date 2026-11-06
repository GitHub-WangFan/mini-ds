package com.minids;

public class ThreadLocalDemo {

//    static ThreadLocal<String> context = new ThreadLocal<>();

    static String context;
    public static void main(String[] args) {
        Thread threadA = new Thread(() -> {
//            context.set("存入线程A的值");
            context = "存入线程A的值";
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }

            System.out.println("取出线程A的值" + context);
//            System.out.println("取出线程A的值" + context.get());
        });

        Thread threadB = new Thread(() -> {
            context = "存入线程B的值";
//            context.set("存入线程B的值");

            System.out.println("取出线程B的值" + context);
//            System.out.println("取出线程B的值" + context.get());
        });

        threadA.start();
        threadB.start();
    }
}
