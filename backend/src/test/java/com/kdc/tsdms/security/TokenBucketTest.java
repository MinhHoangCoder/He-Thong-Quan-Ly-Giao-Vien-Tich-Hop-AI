package com.kdc.tsdms.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * Unit test cho TokenBucket — thuần in-memory, KHÔNG cần DB/Spring/Mockito.
 *
 * <p>Mẹo kiểm thử: truyền {@code refillTokensPerMilli = 0} để xô "đứng yên" theo thời gian
 * (kiểm chứng sức chứa & chặn một cách tất định); dùng sleep ngắn khi cần kiểm chứng việc
 * token tự hồi. Class & các method là package-private nên test phải nằm cùng package.
 */
class TokenBucketTest {

    @Test
    void newBucket_startsFull() {
        TokenBucket bucket = new TokenBucket(5, 0); // refill 0 -> không phụ thuộc thời gian
        assertThat(bucket.isFull()).isTrue();
    }

    @Test
    void consumesUpToCapacityThenBlocks() {
        TokenBucket bucket = new TokenBucket(3, 0);

        assertThat(bucket.tryConsume()).isTrue();
        assertThat(bucket.tryConsume()).isTrue();
        assertThat(bucket.tryConsume()).isTrue();
        assertThat(bucket.tryConsume()).isFalse(); // cạn token -> chặn
    }

    @Test
    void isFull_falseAfterConsuming() {
        TokenBucket bucket = new TokenBucket(2, 0);

        bucket.tryConsume();

        assertThat(bucket.isFull()).isFalse();
    }

    @Test
    void refillsOverTime_allowsAgainAfterWaiting() throws InterruptedException {
        // 1 token mỗi 20ms. Tiêu hết rồi chờ đủ lâu -> hồi token, qua lại được.
        TokenBucket bucket = new TokenBucket(1, 0.05);

        assertThat(bucket.tryConsume()).isTrue();
        assertThat(bucket.tryConsume()).isFalse();

        Thread.sleep(60); // >= 20ms -> hồi >= 1 token

        assertThat(bucket.tryConsume()).isTrue();
    }

    @Test
    void refillNeverExceedsCapacity() throws InterruptedException {
        // Idle đủ lâu để (nếu không chặn) hồi ~10 token, nhưng xô chỉ giữ tối đa capacity.
        TokenBucket bucket = new TokenBucket(2, 0.05);

        Thread.sleep(200); // 0.05 * 200 = 10 token, bị chặn về capacity = 2

        int granted = 0;
        for (int i = 0; i < 6; i++) {
            if (bucket.tryConsume()) {
                granted++;
            }
        }
        // Chỉ ~capacity token khả dụng tức thời (dung sai +1 cho phần hồi cực nhỏ khi đang lặp).
        assertThat(granted).isBetween(2, 3);
    }

    @Test
    void tryConsume_threadSafe_neverOverIssuesTokens() throws InterruptedException {
        int capacity = 100;
        int threads = 200;
        TokenBucket bucket = new TokenBucket(capacity, 0); // refill 0 -> loại yếu tố thời gian
        ExecutorService pool = Executors.newFixedThreadPool(16);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger granted = new AtomicInteger();

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    start.await(); // chờ để thả đồng loạt -> ép tranh chấp
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                if (bucket.tryConsume()) {
                    granted.incrementAndGet();
                }
            });
        }
        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(5, TimeUnit.SECONDS)).isTrue();

        // synchronized đảm bảo cấp ĐÚNG capacity, không phát lố do race (lost update).
        assertThat(granted.get()).isEqualTo(capacity);
    }
}
