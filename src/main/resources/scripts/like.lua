-- KEYS[1]: 点赞者集合 Key (likersSetKey)
-- KEYS[2]: 目标详情 Hash Key (targetHashKey)
-- ARGV[1]: userId (用户ID)
-- ARGV[2]: likeCountFieldName (点赞总数字段名, e.g., "likeCount")
-- ARGV[3]: emptyLikeCacheString (空缓存标记字符串)

local likersSetKey = KEYS[1]
local targetHashKey = KEYS[2]
local userId = ARGV[1]
local likeCountField = ARGV[2]
local emptyString = ARGV[3]

-- 确保操作的不是空标记本身 (不应该通过点赞接口来操作空标记)
if userId == emptyString then
    local count = redis.call('HGET', targetHashKey, likeCountField)
    return {0, tonumber(count or 0)} -- 操作无效，返回当前计数值
end

-- 1. 尝试将用户ID添加到点赞者集合中
local saddResult = redis.call('SADD', likersSetKey, userId)

if saddResult == 1 then
    -- 用户之前不在集合中，成功添加。
    -- 此时，如果空标记在集合中，应该移除它，因为现在有真实用户了。
    redis.call('SREM', likersSetKey, emptyString)

    -- 在Hash中增加点赞数。
    local newCount = redis.call('HINCRBY', targetHashKey, likeCountField, 1)
    return {1, newCount} -- 返回: {状态: 1 (成功点赞), 新的总点赞数}
else
    -- 用户已在集合中 (SADD返回0)，点赞无效。
    -- 不需要查总点赞数/是在hash中进行查询的
    return {0, 0} -- 返回: {状态: 0 (已点赞/操作无效), 总点赞数也直接返回0}
end