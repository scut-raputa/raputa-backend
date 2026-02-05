package cn.scut.raputa.utils;

import lombok.extern.slf4j.Slf4j;

import java.util.LinkedList;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 数据缓冲队列 - 参考原始项目的BufQueue.java
 * 用于缓存IMU和GAS数据，支持多线程安全操作
 * 
 * @author RAPUTA Team
 */
@Slf4j
public class DataBuffer {
    
    private final LinkedList<Object> list = new LinkedList<>();
    // 计数器(同步),判断集合元素数量
    private final AtomicInteger count = new AtomicInteger();
    // 集合上限与下限
    private final int minSize = 0;
    private final int maxSize;
    
    // 构造器指定最大值
    public DataBuffer(int maxSize) {
        this.maxSize = maxSize;
    }
    
    // 初始化对象,用于加锁
    private final Object lock = new Object();
    
    /**
     * put方法:往集合中添加元素,如果集合元素已满,则此线程阻塞,直到有空间再继续
     */
    public void put(Object obj) {
        synchronized (lock) {
            while (count.get() == this.maxSize) {
                try {
                    lock.wait();
                } catch (InterruptedException e) {
                    log.error("数据缓冲队列等待被中断", e);
                    Thread.currentThread().interrupt();
                }
            }
            list.add(obj);
            // 计数器加一
            count.incrementAndGet();
            // 唤醒另一个线程
            lock.notify();
        }
    }
    
    /**
     * getVal方法:从元素中取数据,如果集合为空,则线程阻塞,直到集合不为空再继续
     */
    public Object getVal() {
        Object result = null;
        synchronized (lock) {
            while (count.get() == this.minSize) {
                try {
                    lock.wait();
                } catch (InterruptedException e) {
                    log.error("数据缓冲队列等待被中断", e);
                    Thread.currentThread().interrupt();
                    return null;
                }
            }
            try {
                // 移除第一个
                result = list.removeFirst();
                // 计数器减一
                count.decrementAndGet();
                // 唤醒另一个线程
                lock.notify();
            } catch (NoSuchElementException e) {
                result = null;
                log.warn("数据缓冲队列为空，无法获取元素");
            }
        }
        return result;
    }
    
    /**
     * 获取队列大小
     */
    public int getSize() {
        return this.count.get();
    }
    
    /**
     * 清空队列
     */
    public void clear() {
        synchronized (lock) {
            list.clear();
            count.set(0);
        }
    }
    
    /**
     * 非阻塞获取数据，如果队列为空返回null
     */
    public Object poll() {
        synchronized (lock) {
            if (count.get() == this.minSize) {
                return null;
            }
            try {
                Object result = list.removeFirst();
                count.decrementAndGet();
                lock.notify();
                return result;
            } catch (NoSuchElementException e) {
                return null;
            }
        }
    }
}





