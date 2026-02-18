-- KEYS[1] = "warmest:tail"

local tailKey = KEYS[1]
local tail = redis.call('GET', tailKey)

if tail == false then
    return nil
end

return tail
