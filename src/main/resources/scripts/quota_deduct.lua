local quota = redis.call('GET', KEYS[1])
if quota then
    if tonumber(quota) <= 0 then
        return -1
    end
    local newQuota = redis.call('DECRBY', KEYS[1], tonumber(ARGV[1]))
    return newQuota
else
    local dbQuota = tonumber(ARGV[2])
    if dbQuota <= 0 then
        return -1
    end
    local newQuota = dbQuota - tonumber(ARGV[1])
    redis.call('SET', KEYS[1], newQuota)
    return newQuota
end
