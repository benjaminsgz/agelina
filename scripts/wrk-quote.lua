wrk.method = "POST"
wrk.headers["Content-Type"] = "application/json"

-- 生成随机的请求数据，针对 /quotes/preview 接口
request = function()
    local uid = "u" .. (100 * math.random(1,3))
    local sku = "SKU-100" .. math.random(1,3)
    local body = string.format('{"userId":"%s","sku":"%s","quantity":1}', uid, sku)
    return wrk.format("POST", "/quotes/preview", nil, body)
end
