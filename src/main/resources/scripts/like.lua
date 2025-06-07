-- KEYS[1]: 文章点赞者集合 Key (例如: article:likers:123)
-- KEYS[2]: 文章 Hash Key (例如: article:123)
-- ARGV[1]: userId (用户ID)
-- ARGV[2]: likeCountFieldName (字符串, 例如: "likeCount")

local articleLikersSetKey = KEYS[1]
local articleHashKey = KEYS[2]
local userId = ARGV[1]
local likeCountField = ARGV[2]

-- 尝试将用户ID添加到点赞者集合中
local saddResult = redis.call('SADD', articleLikersSetKey, userId)

if saddResult == 1 then
    -- 用户之前不在集合中，成功添加。在Hash中增加点赞数。
    local newCount = redis.call('HINCRBY', articleHashKey, likeCountField, 1)
    return {1, newCount} -- 返回: {状态: 1 (成功点赞), 新的总点赞数}
else
    -- 用户已在集合中 (重复点赞)。从Hash中获取当前点赞数。
    local currentCount = redis.call('HGET', articleHashKey, likeCountField)
    if not currentCount then
        -- 如果Hash存在，这种情况不常见。可能意味着 likeCountField 还没有被创建。
        -- 为安全起见，如果这是第一次SADD成功，HINCRBY会将其创建并设为1。
        -- 如果SADD返回0，意味着之前点赞过，所以HGET应该能找到一个计数。
        -- 假设在“已点赞”状态后，如果计数丢失，则返回0。
        return {0, 0} -- 返回: {状态: 0 (已点赞), 计数: 0 (或错误指示)}
    end
    return {0, tonumber(currentCount)} -- 返回: {状态: 0 (已点赞), 当前总点赞数}
end