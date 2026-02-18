-- KEYS[1] = "warmest:data"
-- KEYS[2] = "warmest:prev"
-- KEYS[3] = "warmest:next"
-- KEYS[4] = "warmest:head"
-- KEYS[5] = "warmest:tail"
-- ARGV[1] = key

local dataKey = KEYS[1]
local prevKey = KEYS[2]
local nextKey = KEYS[3]
local headKey = KEYS[4]
local tailKey = KEYS[5]
local key = ARGV[1]

-- Get value
local value = redis.call('HGET', dataKey, key)

if value == false then
    return nil
end

-- Remove from data hash
redis.call('HDEL', dataKey, key)

-- Get position
local prevNode = redis.call('HGET', prevKey, key)
local nextNode = redis.call('HGET', nextKey, key)

-- Detach
if prevNode and prevNode ~= false then
    if nextNode and nextNode ~= false then
        redis.call('HSET', nextKey, prevNode, nextNode)
    else
        redis.call('HDEL', nextKey, prevNode)
    end
else
    -- Was head
    if nextNode and nextNode ~= false then
        redis.call('SET', headKey, nextNode)
    else
        redis.call('DEL', headKey)
    end
end

if nextNode and nextNode ~= false then
    if prevNode and prevNode ~= false then
        redis.call('HSET', prevKey, nextNode, prevNode)
    else
        redis.call('HDEL', prevKey, nextNode)
    end
else
    -- Was tail
    if prevNode and prevNode ~= false then
        redis.call('SET', tailKey, prevNode)
    else
        redis.call('DEL', tailKey)
    end
end

-- Clean up node references
redis.call('HDEL', prevKey, key)
redis.call('HDEL', nextKey, key)

return value
