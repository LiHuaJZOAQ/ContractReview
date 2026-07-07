package com.contractreview.async;

import com.contractreview.domain.entity.User;
import com.contractreview.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class QuotaRollbackHandler {

    private final RedisTemplate<String, Object> redisTemplate;
    private final UserMapper userMapper;

    public void rollback(Long userId) {
        String quotaKey = "user:quota:" + userId;
        redisTemplate.opsForValue().increment(quotaKey);
        log.info("Rolled back Redis quota for user: {}", userId);

        User user = userMapper.selectById(userId);
        if (user != null) {
            user.setReviewQuota(user.getReviewQuota() + 1);
            userMapper.updateById(user);
            log.info("Rolled back DB quota for user: {}", userId);
        }
    }
}
