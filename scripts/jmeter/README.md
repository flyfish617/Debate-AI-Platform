# DebateAI JMeter 测试材料

本目录用于存放 DebateAI 的接口冒烟测试和轻量压测脚本。当前脚本重点覆盖项目中适合安全反复请求的接口：

- `GET /api/health`
- `POST /api/auth/login`
- `GET /api/auth/me`
- `GET /api/topic`
- `GET /api/debate`
- `GET /api/leaderboard`

默认压测线程组只访问公开只读接口，不会创建话题、创建辩论、投票、评论或举报，避免压测污染业务数据。

## 文件说明

```text
scripts/jmeter/
├─ debateai-api-smoke-and-load.jmx  # JMeter 测试计划
└─ README.md                        # 运行说明
```

## 前置条件

1. 后端已启动，默认地址为 `http://localhost:8080`。
2. MySQL、Redis、RabbitMQ 按本地配置正常运行。
3. 如果要执行登录相关请求，需要提前准备一个可登录账号。

脚本默认使用：

```text
username=demo
password=demo123456
```

可以通过 JMeter 参数覆盖。

## GUI 运行

1. 打开 JMeter。
2. 选择 `File -> Open`。
3. 打开 `scripts/jmeter/debateai-api-smoke-and-load.jmx`。
4. 在 `Test Plan -> User Defined Variables` 中修改：
   - `host`
   - `port`
   - `username`
   - `password`
5. 点击运行。

## 命令行运行

在项目根目录执行：

```powershell
jmeter -n `
  -t scripts/jmeter/debateai-api-smoke-and-load.jmx `
  -l scripts/jmeter/result.jtl `
  -Jthreads=20 `
  -Jloops=10 `
  -Jramp=10 `
  -Jhost=localhost `
  -Jport=8080 `
  -Jusername=demo `
  -Jpassword=demo123456
```

生成 HTML 报告：

```powershell
jmeter -g scripts/jmeter/result.jtl -o scripts/jmeter/report
```

`result.jtl` 和 `report/` 是运行产物，建议不要提交到仓库。

## 线程组说明

### Smoke - Read and Auth APIs

用于检查后端接口是否可用：

- 健康检查返回 `200`
- 登录接口返回 `200`
- 登录成功后提取 JWT
- 使用 JWT 调用 `/api/auth/me`
- 调用话题、辩论和排行榜公开接口

### Load - Public Read APIs

用于对公开只读接口做轻量压测：

- `/api/topic`
- `/api/debate`
- `/api/leaderboard`

线程数、循环次数和启动时间由参数控制：

```text
threads: 并发线程数，默认 20
loops: 每个线程循环次数，默认 10
ramp: 启动时间，默认 10 秒
```

## 可记录指标

运行后建议记录：

- 样本数
- 平均响应时间
- P90 / P95 / P99 响应时间
- 最小/最大响应时间
- 错误率
- 吞吐量
- Redis 与 MySQL 运行状态

如果用于简历，必须写真实压测环境和真实测试结果，不要直接填写估算值。

## 后续可扩展测试

后续如果需要验证写接口，可以单独准备隔离测试环境和测试账号，再扩展：

- 登录限流：连续请求 `/api/auth/login`，验证超过阈值后返回 `429`。
- 创建辩论链路：创建话题、创建辩论、发送消息、结束辩论。
- 评论/投票/举报链路：使用测试数据验证权限和重复提交规则。
- 排行榜缓存：对比缓存命中与缓存失效重建时的响应时间。

写接口压测会产生数据，建议只在独立测试库执行。
