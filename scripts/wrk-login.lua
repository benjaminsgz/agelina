wrk.method = "POST"
wrk.headers["Content-Type"] = "application/json"

request = function()
    local body = '{"username":"demo","password":"password123"}'
    return wrk.format("POST", "/auth/login", nil, body)
end