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

-- 确保操作的不是空标记本身
if userId == emptyString then
    local count = redis.call('HGET', targetHashKey, likeCountField)
    return {0, tonumber(count or 0)} -- 操作无效
end

-- 1. 尝试从点赞者集合中移除用户ID
local sremResult = redis.call('SREM', likersSetKey, userId)

if sremResult == 1 then
    -- 用户之前在集合中，成功移除。
    -- 在Hash中减少点赞数。
    local newCount = redis.call('HINCRBY', targetHashKey, likeCountField, -1)
    if tonumber(newCount) < 0 then
        newCount = 0
        redis.call('HSET', targetHashKey, likeCountField, '0') -- 确保点赞数不为负
    end

    -- 如果移除用户后，点赞数变为0 (意味着没有其他真实点赞者了)
    -- 并且集合中没有其他真实用户了，此时应该将空标记添加回集合，以防止缓存穿透。
    -- 检查集合中是否还有其他真实用户
    local actualMemberCount = 0
    local members = redis.call('SMEMBERS', likersSetKey)
    for _, memberId in ipairs(members) do
        if memberId ~= emptyString then
            actualMemberCount = actualMemberCount + 1
        end
    end

    if actualMemberCount == 0 then
        redis.call('SADD', likersSetKey, emptyString)
    end

    return {1, newCount} -- 返回: {状态: 1 (成功取消点赞), 新的总点赞数}
else
    -- 用户之前不在集合中 (未点赞或已取消)。
    return {0,  0} -- 返回: {状态: 0 (本未点赞/操作无效), 当前总点赞数无效}
end