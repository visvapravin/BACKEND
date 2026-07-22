package com.forumx.redis.exception;

/**
 * Domain exception wrapping all Redis infrastructure failures.
 *
 * <p>Business modules must never catch or declare raw Spring
 * {@code DataAccessException} or Lettuce exceptions. All Redis errors surface
 * through this exception, keeping the infrastructure detail hidden from callers.
 */
public class RedisOperationException extends RuntimeException {

    public RedisOperationException(String message) {
        super(message);
    }

    public RedisOperationException(String message, Throwable cause) {
        super(message, cause);
    }
}
