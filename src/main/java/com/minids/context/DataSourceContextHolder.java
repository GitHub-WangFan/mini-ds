package com.minids.context;

public class DataSourceContextHolder {

    //私有构造器 无参构造（默认会有 但添加有参构造后会被覆盖 因此手动添加上） 防止报错
    private DataSourceContextHolder(){};

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
