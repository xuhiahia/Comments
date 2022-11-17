package com.hmdp.utils;

public interface ILock {

    public boolean tryLock(Long milltime);

    public void unLock();
}
