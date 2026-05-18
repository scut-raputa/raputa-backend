package cn.scut.raputa.utils;

import lombok.extern.slf4j.Slf4j;

import java.util.LinkedList;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
public class DataBuffer {

    private final LinkedList<Object> list = new LinkedList<>();

    private final AtomicInteger count = new AtomicInteger();

    private final int minSize = 0;
    private final int maxSize;

    public DataBuffer(int maxSize) {
        this.maxSize = maxSize;
    }

    private final Object lock = new Object();

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

            count.incrementAndGet();

            lock.notify();
        }
    }

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

                result = list.removeFirst();

                count.decrementAndGet();

                lock.notify();
            } catch (NoSuchElementException e) {
                result = null;
                log.warn("数据缓冲队列为空，无法获取元素");
            }
        }
        return result;
    }

    public int getSize() {
        return this.count.get();
    }

    public void clear() {
        synchronized (lock) {
            list.clear();
            count.set(0);
        }
    }

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

