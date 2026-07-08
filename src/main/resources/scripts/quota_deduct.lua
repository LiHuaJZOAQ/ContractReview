local quota = redis.call('GET', KEYS[1])
if not quota or tonumber(quota) <= 0 then
    return -1
end
local newQuota = redis.call('DECRBY', KEYS[1], tonumber(ARGV[1]))
return newQuota
