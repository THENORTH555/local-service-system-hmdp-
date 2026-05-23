package com.hmdp.utils;

/**
 * ClassName:Ilock
 * Description:
 *
 * @Author 何永琪
 * @Create 2025/10/18 17:13
 * @Version 1.0
 */
public interface Ilock {
    boolean tryLock(long timeoutSec);
    void unLock();
}
