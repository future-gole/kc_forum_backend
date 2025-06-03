-- KEYS[1]: userLikesTargetKey (e.g., likes:user:article:123)
-- 脚本的第一个键参数 (KEYS[1])：表示某个用户是否点赞了某个目标的键
-- 例如："likes:user:article:123" 表示用户点赞文章123的集合

-- KEYS[2]: targetLikeCountKey (e.g., likes:count:article:123)
-- 脚本的第二个键参数 (KEYS[2])：表示某个目标总点赞数的键
-- 例如："likes:count:article:123" 表示文章123的总点赞数

-- ARGV[1]: userId
-- 脚本的第一个参数 (ARGV[1])：表示操作用户的ID

local userLikesKey = KEYS[1] -- 将传入的第一个键名赋值给局部变量 userLikesKey
local countKey = KEYS[2]     -- 将传入的第二个键名赋值给局部变量 countKey
local userId = ARGV[1]       -- 将传入的第一个参数值赋值给局部变量 userId

-- SADD 将点赞成员添加到 userLikesKey set集合中
-- redis.call('SADD', key, member) 命令尝试将 member 添加到名为 key 的 Set 集合中。
-- 如果 member 成功添加（即之前不存在于集合中），命令返回 1。
-- 如果 member 已经是集合的成员，命令返回 0。
local saddResult = redis.call('SADD', userLikesKey, userId)

if saddResult == 1 then
    -- 如果 saddResult 等于 1，表示 userId 之前没有点赞过，现在成功点赞
    -- 不在则计数加一
    -- redis.call('INCR', key) 命令将名为 key 的字符串值解释为整数，并将其递增 1。
    -- 如果 key 不存在，它会在执行操作前被设置为 0。
    -- 命令返回递增后的值。
    local newCount = redis.call('INCR', countKey)
    return {1, newCount} -- 返回一个包含两个元素的 Lua 表 (table)
    -- 第一个元素是状态码：1 表示“本次操作成功点赞”
    -- 第二个元素是新的总点赞数 newCount
else
    -- 如果 saddResult 等于 0，表示 userId 之前已经点赞过了
    -- User was already in the set (already liked), do not increment count
    -- (用户已在集合中（已点赞），不增加计数)
    -- Get the current count to return it
    -- (获取当前计数并返回)
    local currentCount = redis.call('GET', countKey) -- 获取目标点赞总数键的当前值

    -- If countKey doesn't exist yet (e.g., after SADD 0 but before any INCR),
    -- INCR would create it at 1. GET would return nil.
    -- (如果 countKey 尚不存在（例如，在 SADD 返回 0 但之前没有任何 INCR 操作的情况下），
    -- INCR 会将其创建并设置为 1。而 GET 会返回 nil。)
    -- For consistency, if it's nil, this implies an issue or first like not yet counted.
    -- (为了保持一致性，如果它是 nil，则意味着存在问题或首次点赞尚未计数。)
    -- However, if saddResult is 0, countKey should ideally exist from a previous INCR.
    -- (然而，如果 saddResult 是 0，理想情况下 countKey 应该因为之前的 INCR 操作而存在。)
    if not currentCount then
        -- This case should ideally not be hit if the system is consistent.
        -- (如果系统一致，理想情况下不应命中此情况。)
        -- If it is, it means the like set has the user, but the count is missing.
        -- (如果命中，则表示点赞集合中有该用户，但计数丢失了。)
        -- We could initialize the count here, or rely on a background job to fix.
        -- (我们可以在此处初始化计数，或依赖后台作业进行修复。)
        -- For now, let's assume if user is in set, count should exist or be creatable.
        -- (目前，我们假设如果用户在集合中，计数应该存在或是可创建的。)
        -- Let's return 0 as if count was missing (or handle as error in Java if preferred)
        -- (让我们返回 0，就好像计数丢失了一样（或者如果愿意，可以在 Java 中作为错误处理）)
        return {0, 0} -- 返回状态码 0 表示“已点赞”，计数为 0 (表示计数异常或缺失)
    end
    -- tonumber() 将 Redis 返回的字符串类型的计数值转换为数字类型
    return {0, tonumber(currentCount)} -- 返回状态码 0 表示“已点赞”，以及当前的总点赞数
end