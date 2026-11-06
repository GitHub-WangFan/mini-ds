package com.minids.context;

public class DataSourceContextHolder {

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
