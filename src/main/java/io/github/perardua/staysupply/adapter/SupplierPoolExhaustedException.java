package io.github.perardua.staysupply.adapter;

import reactor.netty.internal.shaded.reactor.pool.PoolAcquirePendingLimitException;
import reactor.netty.internal.shaded.reactor.pool.PoolAcquireTimeoutException;

import io.github.perardua.staysupply.supplier.Supplier;

/**
 * 커넥션 풀이 포화되어 호출 자체를 하지 못한 경우.
 *
 * <p>공급사가 아니라 우리 쪽 자원이 모자란 것이다. 요청은 나가지 않았다.
 */
public final class SupplierPoolExhaustedException extends SupplierClientException {

    public SupplierPoolExhaustedException(Supplier supplier, String message, Throwable cause) {
        super(supplier, message, cause);
    }

    /**
     * 대기 큐가 꽉 찬 경우와 대기가 상한을 넘긴 경우 둘 다 풀 포화다.
     * 뒤쪽은 pendingAcquireTimeout이 호출 상한보다 길면 도달하지 않지만, 값이 바뀌면 도달한다.
     */
    public static boolean isPoolExhaustion(Throwable throwable) {
        for (Throwable t = throwable; t != null; t = t.getCause()) {
            if (t instanceof PoolAcquirePendingLimitException || t instanceof PoolAcquireTimeoutException) {
                return true;
            }
            if (t.getCause() == t) {
                break;
            }
        }
        return false;
    }
}
