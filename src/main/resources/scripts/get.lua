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

-- Detaches a node from its current position in the linked list
local function detach(key)
    local prevNode = redis.call('HGET', prevKey, key)
    local nextNode = redis.call('HGET', nextKey, key)

    -- Update previous node's next pointer
    if prevNode ~= false and nextNode ~= false then
        redis.call('HSET', nextKey, prevNode, nextNode)
    elseif prevNode ~= false then
        redis.call('HDEL', nextKey, prevNode)
    end

    -- Update next node's prev pointer
    if nextNode ~= false and prevNode ~= false then
        redis.call('HSET', prevKey, nextNode, prevNode)
    elseif nextNode ~= false then
        redis.call('HDEL', prevKey, nextNode)
    end
end

-- Attaches a node to the tail of the linked list (making it the warmest)
local function attachToTail(key)
    local currentTail = redis.call('GET', tailKey)

    if currentTail ~= false then
        redis.call('HSET', nextKey, currentTail, key)
        redis.call('HSET', prevKey, key, currentTail)
    end

    redis.call('HDEL', nextKey, key)
    redis.call('SET', tailKey, key)
end

-- Moves an existing node to the tail position (making it the warmest)
local function moveToTail(key)
    local currentTail = redis.call('GET', tailKey)

    if currentTail == key then
        -- Already at tail, nothing to do
        return
    end

    detach(key)
    attachToTail(key)
end

-- Main logic
local value = redis.call('HGET', dataKey, key)

if value == false then
    return nil
end

moveToTail(key)
return value
