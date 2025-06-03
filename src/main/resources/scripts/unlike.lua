local userLikesKey = KEYS[1] -- 将传入的第一个键名赋值给局部变量 userLikesKey
local countKey = KEYS[2]     -- 将传入的第二个键名赋值给局部变量 countKey
local userId = ARGV[1]       -- 将传入的第一个参数值赋值给局部变量 userId

local saddResult = redis.call('SREM',userLikesKey,userId)
-- 成功
if saddResult == 1 then
    local newCount = redis.call('DECR',countKey)
    -- count小于0置为0
    if tonumber(newCount) < 0 then
        redis.call('SET',countKey,'0')
        newCount = 0;
    end
    return {1,newCount}
    --用户不在set中
else
    local currentCount = redis.call('GET',countKey)
    -- 异常处理
    if not currentCount then
        return {0,0}
    end
    return {0,currentCount}
end
