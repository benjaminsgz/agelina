# wrk Pressure Test Report

Date: 2026-04-13 UTC

Target:
- URL: `http://127.0.0.1:8080/auth/login`
- Method: `POST`
- Body: `{"username":"demo","password":"password123"}`
- Database: `threadpool-mysql:3306/threadpool_demo`

Command template:

```bash
wrk -t8 -c200 -d30s --timeout 20s --latency -s /workspace/ThreadPool/scripts/wrk-login.lua http://127.0.0.1:8080/auth/login
wrk -t8 -c500 -d30s --timeout 20s --latency -s /workspace/ThreadPool/scripts/wrk-login.lua http://127.0.0.1:8080/auth/login
```

Notes:
- `wrk` default timeout is 2s. This endpoint includes JDBC lookup and bcrypt verification, so timeout was raised to `20s` to avoid counting normal queueing as network failure.
- No socket errors, timeout errors, or non-2xx/3xx counts were reported by `wrk` in the formal runs.

## Scenario A

- Threads: `8`
- Connections: `200`
- Duration: `30s`

```text
Running 30s test @ http://127.0.0.1:8080/auth/login
  8 threads and 200 connections
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency     4.46s     3.63s   14.71s    77.00%
    Req/Sec    10.92      9.44    60.00     83.43%
  Latency Distribution
     50%    2.99s
     75%    3.36s
     90%   10.29s
     99%   14.51s
  1398 requests in 30.09s, 226.63KB read
Requests/sec:     46.46
Transfer/sec:      7.53KB
```

## Scenario B

- Threads: `8`
- Connections: `500`
- Duration: `30s`

```text
Running 30s test @ http://127.0.0.1:8080/auth/login
  8 threads and 500 connections
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency     7.77s     2.46s   10.88s    80.65%
    Req/Sec    15.47     14.56    80.00     85.84%
  Latency Distribution
     50%    8.70s
     75%    9.19s
     90%    9.76s
     99%   10.60s
  1607 requests in 30.09s, 260.51KB read
Requests/sec:     53.40
Transfer/sec:      8.66KB
```

## Summary

- The login chain is functional under pressure.
- Throughput is capped around `46-53 RPS` in these runs.
- Higher concurrency slightly increases throughput but significantly raises latency.
- The main bottleneck is the CPU-bound bcrypt verification stage, which queues behind the current CPU pool configuration.
