# 优惠券 k6 测试

三个入口对应性能、超卖、一人一单。当前验证同步接口：免费领券产生 `user_coupon`，购买代金券只创建待支付 `coupon_order`，不会调用支付接口。

## 第一次运行

先启动项目后端、MySQL、Redis。在项目根目录执行以下命令。准备脚本使用 Python 3 标准库，不需要 pip 或 npm 安装依赖。

```bash
python3 tests/k6/coupon/prepare.py
```

按提示输入商家管理员密码，默认管理员账号 `admin`。脚本会通过正常接口创建1000个独立用户，提前登录取得 Token，再发布、启售6张专用券，分别用于三个测试的领取和购买场景。注册/登录不计入压测。

生成的 `data/local.json` 含本地 Token，已通过本目录 `.gitignore` 忽略。准备过程不会清理已有记录。请在本地测试数据环境运行；大批注册会执行密码哈希，需要等待。

### 1. 性能测试

默认按完整流程执行：同一个用户先查询列表，从列表找到两张专用券，再领取满减券，最后创建代金券订单。用户之间并发，单个用户内部的三步顺序执行，任一步失败就结束该用户本轮流程。

```bash
# 1000个不同用户，每人查询+领取+购买，共3000次目标HTTP请求，100个并发执行用户
k6 run tests/k6/coupon/performance.js
```

报告同时给出：

- 整批用户流程从首次执行到最后一次结束的总耗时，排除setup预检查和k6启动时间。
- 单个完整流程的平均/P95/P99耗时。
- 查询、领取、购买三个接口分别的平均/P95/P99耗时。
- 完整流程成功数、HTTP请求成功数、异常数，避免将失败后的快速结束当作性能提升。

这个总耗时包含三步网络请求和脚本中的结果检查，不包含注册、登录、用户思考时间与支付。领取满减券和购买代金券是两种不同操作，此流程让每个用户各领/买一张，覆盖三种操作。不是同一张券既领取又购买。

完整流程一次会消耗两张性能测试专用券的库存。想单独定位某个接口时，也可使用以下模式，但必须重新prepare，避免与刚运行的完整流程共用已消耗的券：

```bash
# 只查列表，查询模式可以重复运行
k6 run -e ACTION=list tests/k6/coupon/performance.js

# 只领取满减券
k6 run -e ACTION=claim tests/k6/coupon/performance.js

# 只创建待支付购券订单
k6 run -e ACTION=buy tests/k6/coupon/performance.js
```

性能测试库存充足、用户各不相同，所有流程均应成功；库存不足、重复购买、401、500都记为异常。报告保留分接口耗时，方便判断是查列表、领取还是建单变快，不能只看三个接口的混合平均数。

### 2. 超卖测试

```bash
# 1000个不同用户抢100张代金券，预期100次成功、900次库存不足
k6 run tests/k6/coupon/oversell.js

# 另用1000个不同用户身份抢100张满减券
k6 run -e ACTION=claim tests/k6/coupon/oversell.js
```

### 3. 一人一单测试

```bash
# 100个虚拟用户共用同一真实用户的 Token，并发提交100次，预期1次成功
k6 run tests/k6/coupon/single-user.js

# 免费领券也独立验证
k6 run -e ACTION=claim tests/k6/coupon/single-user.js
```

该测试默认券库存1000，确保重复请求被拒绝是由于限购而非售罄。多个 VU 使用同一个 Token，模拟同用户并发请求，并非100个不同用户。

## 如何判断结果

控制台输出目标/实际请求数、业务成功数、预期拒绝数、异常数，以及平均/P95/P99响应耗时。P95表示95%的请求耗时不超过该数值。断言不通过时 k6 返回非0退出码；保留失败结果，优化后再比较，不要改断言让现有问题通过。

JSON报告自动存入 `tests/k6/coupon/results/`，文件名包含标签、场景、时间戳。报告只写指标和测试券ID，不复制 Token。`coupon_flow_duration`统计完整流程，`coupon_list_duration`/`coupon_claim_duration`/`coupon_buy_duration`分别统计三个接口。`coupon_duration`是目标HTTP请求的混合指标；k6内置HTTP指标还包含setup里的鉴权/专用券检查。

**HTTP断言通过还不足以证明没有超卖。** 每次写入测试后，打开 `verify.sql`，填入刚才控制台/JSON里的优惠券ID和初始库存，在测试数据库执行只读查询：

- `stock_matches=1`，`oversold=0`，剩余库存不能为负。
- 重复订单、重复发券查询均返回0行。
- 默认超卖测试实际分配100张，一人一单实际分配1张；对比HTTP成功数与数据库记录数。
- 完整流程需对报告中的两张券分别核对：每张应分配1000张。3000次成功HTTP请求包含1000次列表查询，不能理解为产生3000笔订单。
- 购券订单15分钟后会自动关闭并归还库存，因此应在测试结束后立即核对。

目前没有Redis券库存，不能虚构Redis一致性检查。接入后补充Redis库存与数据库核对；异步建单后还要等待最终落库，当前脚本不能把“已受理”当成功订单。

## 增加规模、对比优化前后

默认1000个不同用户、100个并发执行用户。仍需根据机器能力逐级增加，而非把“用户总数”当作“同时并发数”。每档都要新准备一组数据：

| 档位 | 总用户/完整流程数 | 并发执行用户 | 目标HTTP请求数 |
|---|---:|---:|---:|
| 小规模确认 | 100 | 10 | 300 |
| 默认对比 | 1000 | 100 | 3000 |
| 增加压力 | 5000 | 200 | 15000 |
| 更高并发 | 5000 | 500 | 15000 |

小规模确认可执行 `python3 tests/k6/coupon/prepare.py --users 100 --oversell-stock 10`，然后 `k6 run -e VUS=10 tests/k6/coupon/performance.js`。默认对比命令：

```bash
# 每轮重新准备新账号、新券，避免上轮限购记录干扰
python3 tests/k6/coupon/prepare.py --users 1000 --oversell-stock 100 --label baseline
k6 run -e VUS=100 tests/k6/coupon/performance.js

# 完成缓存优化后，使用相同用户数量/库存/并发/机器配置再跑
python3 tests/k6/coupon/prepare.py --users 1000 --oversell-stock 100 --label cached
k6 run -e VUS=100 tests/k6/coupon/performance.js
```

增加压力时使用 `prepare.py --users 5000 --oversell-stock 500`，再设置 `VUS=200` 或 `VUS=500`。每次新一轮都先重新prepare；不要用同一组性能专用券连续跑两个并发档位。

每个写入场景都使用新券；setup会检查数据库库存是否仍等于初始值，发现使用过会拒绝重跑。`prepare.py`默认重新生成整组数据，旧账号、券、订单仍保留，不会自动删除。列表测试会受到活动券数量影响：对比时保持列表数据规模一致，例如将前一轮专用券通过商家端停售后再准备同等数量的新券。

固定日志级别、JVM参数、连接池、数据规模和缓存状态；先用独立数据预热应用，正式测量至少重复3轮并保留全部结果。这里用固定总流程数、固定并发完成一次批次，不是固定RPS测试；不要把配置的VUS写成实际吞吐。完整流程报告里的 `successfulFlowsPerSecond` 是成功流程/秒，`successfulResponsesPerSecond` 是成功HTTP响应/秒，二者口径不同，都不是最大稳定TPS。

脚本不自动统计数据库SQL次数。需要该指标时，可结合现有 `PerformanceBenchmarkSupport.SqlCountInterceptor` 的方法另加服务端计数，再与同一轮报告对应，不能由k6响应时间推算SQL次数。

## 测试后清理数据

先保存测试报告和数据库核对结果，再停止后端及压测。在数据库客户端选择本项目的测试数据库，打开本目录的 `cleanup.sql`：

1. 保持 `SET @apply = 0;`，完整执行，先看目标数量和检查结果。此时不会删除数据。
2. `@run_id = NULL` 表示匹配所有prepare批次，也可填入具体批次号，只清理一轮。批次号位于测试账号 `k6c_批次号_00000`、测试券 `k6_批次号_p_c` 中。
3. 检查 `blockers=0`、待清理券名及数量正确，再将 `SET @apply = 1;` 并重新完整执行，清理测试购券订单、已领取的测试券、测试券模板和测试用户。

脚本严格匹配prepare的命名格式，不会清空表，不重置自增ID，测试报告也保留。若测试账号/券关联了外卖订单、使用记录、真实用户、其他券、已支付订单、地址或购物车，`blockers`大于0时整批拒绝删除，需先检查关联情况。

不要重新执行 `db/azure.sql` 来恢复，它会重建表。清理后原 `data/local.json` 里的用户和券已失效，下次运行先重新prepare。以后接入Redis券缓存后，还需按这些测试券ID精确清理相关Key，不要清空整个Redis库。

## 参数

| 参数 | 默认值 | 说明 |
|---|---|---|
| `ACTION` | 性能为`flow`，其他为`buy` | `flow`查询→领取→购买；`list`/`claim`/`buy`可单独测接口 |
| `VUS` | 100（不超过轮次数） | 并发虚拟用户数；正确性测试至少2 |
| `REQUESTS` | 性能/超卖为准备的用户数；一人一单为100 | 总流程/轮次数；flow每轮3次HTTP请求；不同用户写入不能超过准备的用户数 |
| `BASE_URL` | `http://localhost:8080` | 后端直连地址；prepare和k6都支持 |
| `LABEL` | 准备时的标签 | 报告标签 |
| `DATA_FILE` | `./data/local.json` | 相对于脚本目录的数据文件路径 |
| `USER_INDEX` | 0 | 一人一单使用第几个测试用户（从0开始） |
| `MAX_DURATION` | `5m` | 最长运行时间；超时少发请求会断言失败 |
| `REQUEST_TIMEOUT` | `10s` | 单个目标请求超时 |

准备脚本还支持 `--base-url`、`--admin`、`--workers`、`--single-stock`、`--output`。管理员密码可通过 `ADMIN_PASSWORD` 环境变量传入，测试用户密码可通过 `K6_USER_PASSWORD` 传入，默认测试用户密码为 `K6Coupon123!`。

例如后端不是8080端口：

```bash
python3 tests/k6/coupon/prepare.py --base-url http://localhost:8081
k6 run tests/k6/coupon/performance.js
```

地址会保存到本地数据文件，之后k6无需重复指定。Token默认2小时过期，过期后重新prepare。正式压测阶段不要把注册、登录或支付混入领券/建单计时。
