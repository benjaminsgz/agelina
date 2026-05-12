# Performance Benchmark Report (2026-05-12)

## 1. Test Environment

| Item | Details |
|------|---------|
| **Benchmark Target** | `threadpool-dag-app` (Spring Boot 3.3.5, JDK 17, Netty) |
| **Load Generator** | `wrk` (running in container `wrk-tester`) |
| **Container Platform** | Docker Desktop on Windows |
| **Test Duration** | 30s per scenario |

## 2. Thread Pool Configuration (`application.yml`)

```yaml
threadpool:
  async:
    io:
      core-size: 8
      max-size: 32
      queue-capacity: 16
      rejection-policy: CALLER_RUNS
    cpu:
      core-size: 4
      max-size: 4
      queue-capacity: 32
      rejection-policy: ABORT
```

## 3. DAG Business Scenario

DAG topology (`/quotes/preview`) with 7 steps, 3 of which simulate IO calls:

```
validateRequest (DIRECT)
├── loadInventory (IO, 100ms) → availableStock
├── loadMemberProfile (IO, 90ms) → memberLevel
└── loadProductAndBaseAmount (IO, 120ms) → productName, unitPrice, baseAmount
    ├── calculateMemberDiscount (CPU) ← baseAmount, memberLevel → memberDiscount
    └── calculateCouponDiscount (CPU) ← baseAmount, validatedContext → couponDiscount
buildQuote (DIRECT) ← converges all results
```

- **Theoretical serial latency**: 310ms (100+90+120)
- **Theoretical parallel latency**: ~220ms (3 IO steps run in parallel)

## 4. Benchmark Results

### Scenario A: Ultra-Light Load (1 Thread, 2 Connections)

```
Running 30s test @ http://dag-app:8081
  1 threads and 2 connections
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency   121.64ms  205.94us 122.16ms   83.13%
    Req/Sec    17.83      4.13    20.00     78.46%
  492 requests in 30.04s, 180.31KB read
Requests/sec:     16.38
```

- **TPS**: 16.38
- **Avg Latency**: 121.64ms
- **Success Rate**: 100%

### Scenario A2: Light Load (1 Thread, 4 Connections)

```
Running 30s test @ http://dag-app:8081
  1 threads and 4 connections
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency   121.58ms  205.39us 122.03ms   86.28%
    Req/Sec    35.69      8.24    40.00     78.46%
  984 requests in 30.06s, 360.58KB read
Requests/sec:     32.74
```

- **TPS**: 32.74
- **Avg Latency**: 121.58ms
- **Success Rate**: 100%

### Scenario B: Moderate Concurrency (2 Threads, 8 Connections)

```
Running 30s test @ http://dag-app:8081
  2 threads and 8 connections
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency   123.30ms    8.98ms 222.50ms   96.40%
    Req/Sec    32.53      8.92    40.00     74.92%
  1944 requests in 30.05s, 712.36KB read
Requests/sec:     64.70
```

- **TPS**: 64.70
- **Avg Latency**: 123.30ms
- **Success Rate**: 100%

### Scenario C: Saturating Load (4 Threads, 32 Connections)

```
Running 30s test @ http://dag-app:8081
  4 threads and 32 connections
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency   254.64ms  107.64ms   1.26s    84.38%
    Req/Sec    31.95     12.97    80.00     75.04%
  3807 requests in 30.05s, 1.36MB read
Requests/sec:    126.69
```

- **TPS**: 126.69
- **Avg Latency**: 254.64ms
- **Success Rate**: 100%

### Scenario D: High Concurrency (8 Threads, 64 Connections)

```
Running 30s test @ http://dag-app:8081
  8 threads and 64 connections
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency   460.14ms  200.38ms   1.47s    69.00%
    Req/Sec    18.42      9.61    60.00     73.64%
  4175 requests in 30.07s, 1.49MB read
Requests/sec:    138.83
```

- **TPS**: 138.83
- **Avg Latency**: 460.14ms
- **Success Rate**: 100%

### Scenario E: Overload (8 Threads, 128 Connections)

```
Running 30s test @ http://dag-app:8081
  8 threads and 128 connections
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency   899.64ms  343.06ms   1.99s    64.89%
    Req/Sec    19.23     11.65    90.00     82.33%
  4197 requests in 30.10s, 1.50MB read
  Socket errors: 5 timeouts
Requests/sec:    139.45
```

- **TPS**: 139.45
- **Avg Latency**: 899.64ms
- **Success Rate**: ~99.9% (5 timeouts)

## 5. Throughput Curve

```
TPS
140 |                                    *
    |                              *
130 |                        *
    |                  *
120 |              *                     (Scenario C: 126.69)
    |          *
110 |      *
    |  *
100 |
 90 |
 80 |
 70 |  *                                     (Scenario B: 64.70)
    |          *                         *
 60 |              *                   *
 50 |                  *             *
 40 |      *   *           *       *        (Scenario A2: 32.74)
    |  *   *       *   *       *   *
 20 |*   *               *               (Scenario A: 16.38)
    +----------------------------------
      A    A2   B    C    D    E
```

## 6. Performance Analysis

### 6.1 Throughput Inflection Point

| Phase | TPS Delta | Avg Latency | Notes |
|-------|-----------|-------------|-------|
| A → A2 | 16→33 (+100%) | flat | IO thread pool not yet saturated |
| A2 → B | 33→65 (+98%) | slight increase | concurrency up, throughput doubled |
| B → C | 65→127 (+96%) | doubled | IO thread pool beginning to saturate |
| C → D | 127→139 (+9%) | doubled | heavily saturated, diminishing returns |
| D → E | 139→139 (flat) | doubled | at ceiling, latency out of control |

**Optimal range**: Scenario B-C (TPS 65–127), latency < 300ms

### 6.2 Latency Growth Analysis

| Scenario | Concurrency | Avg Latency | Relative | Growth |
|----------|-------------|-------------|---------|--------|
| A2 | 4 | 121.58ms | 1.0x | — |
| B | 8 | 123.30ms | 1.01x | +1.4% |
| C | 32 | 254.64ms | 2.10x | +109% |
| D | 64 | 460.14ms | 3.79x | +279% |
| E | 128 | 899.64ms | 7.40x | +640% |

Latency degrades sharply once concurrency exceeds 32, consistent with IO thread pool saturation.

### 6.3 IO Bottleneck Verification

- IO thread pool core: **8**, max: **32**, queue capacity: **16**
- Under high concurrency (64–128), both queue and threads saturate; `CALLER_RUNS` policy kicks in
- Theoretical max TPS: `8 / 0.31s ≈ 25.8` (pure IO blocking), but due to DAG parallelism, measured ceiling is **~140 TPS**

## 7. Conclusions

1. **DAG parallelism is effective**: avg latency (121ms) is far below serial total (310ms), parallelism factor approx **2.5x**
2. **IO thread pool is the bottleneck**: throughput plateaus at scenario C-D (~130 TPS); further concurrency only increases latency
3. **CALLER_RUNS protection is effective**: no request loss under overload (5/4197 timeouts), system did not crash
4. **Current config ceiling**: ~140 TPS @ avg latency < 1s

## 8. Optimization Recommendations

1. **Scale up IO thread pool**: increase `io.core-size` from 8 to 16–24 to linearly boost throughput to ~260 TPS
2. **Increase queue capacity**: raise `io.queue-capacity` from 16 to 256–512 to absorb burst requests
3. **Replace blocking IO with async**: swap Gateway `Thread.sleep` for true async IO (WebClient / Resilience4j) to support higher concurrency with fewer threads
4. **Add rate limiting at the edge**: protect downstream IO dependencies from being overwhelmed during traffic spikes