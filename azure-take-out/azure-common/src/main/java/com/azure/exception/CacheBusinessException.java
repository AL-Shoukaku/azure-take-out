package com.azure.exception;

/**
 * 缓存加载和数据转换失败时抛出的业务异常。
 */
public class CacheBusinessException extends BaseException {

    public CacheBusinessException(String msg) {
        super(msg);
    }

    public CacheBusinessException(String msg, Throwable cause) {
        super(msg, cause);
    }
}
