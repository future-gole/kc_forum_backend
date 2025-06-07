-- KEYS[1]: 文章点赞者集合 Key (例如: article:likers:123)
-- KEYS[2]: 文章 Hash Key (例如: article:123)
-- ARGV[1]: userId (用户ID)
-- ARGV[2]: likeCountFieldName (字符串, 例如: "likeCount")

local articleLikersSetKey = KEYS[1]
local articleHashKey = KEYS[2]
local userId = ARGV[1]
local likeCountField = ARGV[2]

-- 尝试从点赞者集合中移除用户ID
local sremResult = redis.call('SREM', articleLikersSetKey, userId)

if sremResult == 1 then
    -- 用户之前在集合中，成功移除。在Hash中减少点赞数。
    local newCount = redis.call('HINCRBY', articleHashKey, likeCountField, -1)
    if tonumber(newCount) < 0 then
        newCount = 0
        redis.call('HSET', articleHashKey, likeCountField, '0') -- 确保点赞数不为负
    end
    return {1, newCount} -- 返回: {状态: 1 (成功取消点赞), 新的总点赞数}
else
    -- 用户之前不在集合中 (未点赞或已取消)。从Hash中获取当前点赞数。
    local currentCount = redis.call('HGET', articleHashKey, likeCountField)
    if not currentCount then
        return {0, 0} -- 如果计数不存在，返回0
    end
    return {0, tonumber(currentCount)} -- 返回: {状态: 0 (本未点赞), 当前总点赞数}
end