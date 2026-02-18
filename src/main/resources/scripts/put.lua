-- KEYS[1] = "warmest:data"
-- KEYS[2] = "warmest:prev"
-- KEYS[3] = "warmest:next"
-- KEYS[4] = "warmest:head"
-- KEYS[5] = "warmest:tail"
-- ARGV[1] = key
-- ARGV[2] = value

local dataKey = KEYS[1]
local prevKey = KEYS[2]
local nextKey = KEYS[3]
local headKey = KEYS[4]
local tailKey = KEYS[5]
local key = ARGV[1]
local value = ARGV[2]

-- Get previous value
local previousValue = redis.call('HGET', dataKey, key)

-- Check if key exists
local exists = previousValue ~= false

if exists then
    -- Key exists: update value and move to tail
    redis.call('HSET', dataKey, key, value)

    -- Get current position
    local prevNode = redis.call('HGET', prevKey, key)
    local nextNode = redis.call('HGET', nextKey, key)
    local currentTail = redis.call('GET', tailKey)

    -- If already tail, nothing to do for position
    if currentTail ~= key then
        -- Detach from current position
        if prevNode and prevNode ~= false then
            if nextNode and nextNode ~= false then
                redis.call('HSET', nextKey, prevNode, nextNode)
            else
                redis.call('HDEL', nextKey, prevNode)
            end
        else
            -- This was head
            if nextNode and nextNode ~= false then
                redis.call('SET', headKey, nextNode)
            end
        end

        if nextNode and nextNode ~= false then
            if prevNode and prevNode ~= false then
                redis.call('HSET', prevKey, nextNode, prevNode)
            else
                redis.call('HDEL', prevKey, nextNode)
            end
        end

        -- Attach to tail
        if currentTail and currentTail ~= false then
            redis.call('HSET', nextKey, currentTail, key)
        end
        redis.call('HSET', prevKey, key, currentTail)
        redis.call('HDEL', nextKey, key)
        redis.call('SET', tailKey, key)
    end
else
    -- Key doesn't exist: create and add to tail
    redis.call('HSET', dataKey, key, value)

    local currentTail = redis.call('GET', tailKey)
    local currentHead = redis.call('GET', headKey)

    if currentTail and currentTail ~= false then
        redis.call('HSET', nextKey, currentTail, key)
        redis.call('HSET', prevKey, key, currentTail)
    end

    redis.call('SET', tailKey, key)

    if not currentHead or currentHead == false then
        redis.call('SET', headKey, key)
    end
end

return previousValue
