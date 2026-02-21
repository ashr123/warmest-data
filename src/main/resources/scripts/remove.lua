-- KEYS[1] = "warmest:data"
-- KEYS[2] = "warmest:prev"
-- KEYS[3] = "warmest:next"
-- KEYS[4] = "warmest:tail"
-- ARGV[1] = key

local dataKey = KEYS[1]
local prevKey = KEYS[2]
local nextKey = KEYS[3]
local tailKey = KEYS[4]
local key = ARGV[1]

-- Detaches a node from the linked list and handles tail update if needed
local function detach(key)
    local prevNode = redis.call('HGET', prevKey, key)
    local nextNode = redis.call('HGET', nextKey, key)

    -- Update previous node's next pointer
    if prevNode ~= false and nextNode ~= false then
        redis.call('HSET', nextKey, prevNode, nextNode)
    elseif prevNode ~= false then
        redis.call('HDEL', nextKey, prevNode)
    end

    -- Update next node's prev pointer or update tail if this was the tail
    if nextNode ~= false and prevNode ~= false then
        redis.call('HSET', prevKey, nextNode, prevNode)
    elseif nextNode ~= false then
        redis.call('HDEL', prevKey, nextNode)
    elseif prevNode ~= false then
        -- Node was tail
        redis.call('SET', tailKey, prevNode)
    else
        -- Node was the only element
        redis.call('DEL', tailKey)
    end

    -- Clean up node references
    redis.call('HDEL', prevKey, key)
    redis.call('HDEL', nextKey, key)
end

-- Main logic
local value = redis.call('HGET', dataKey, key)

if value == false then
    return nil
end

-- Remove from data hash
redis.call('HDEL', dataKey, key)

-- Detach from linked list
detach(key)

return value
