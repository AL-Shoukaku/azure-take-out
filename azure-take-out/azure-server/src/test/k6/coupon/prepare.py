#!/usr/bin/env python3
"""通过现有 HTTP 接口准备专用账号、Token 和测试券；不连接或清理数据库。"""
import argparse
import concurrent.futures
import getpass
import json
import os
from pathlib import Path
import secrets
import sys
from datetime import datetime, timedelta
from urllib.error import HTTPError, URLError
from urllib.parse import urlencode
from urllib.request import Request, urlopen
from zoneinfo import ZoneInfo


def api(base_url, method, path, body=None, headers=None):
    data = None if body is None else json.dumps(body, ensure_ascii=False).encode()
    request = Request(base_url + path, data=data, method=method,
                      headers={"Content-Type": "application/json", **(headers or {})})
    try:
        with urlopen(request, timeout=30) as response:
            result = json.load(response)
    except HTTPError as error:
        raise RuntimeError(f"{method} {path} 返回 HTTP {error.code}") from None
    except (URLError, TimeoutError, OSError, ValueError) as error:
        raise RuntimeError(f"{method} {path} 请求失败：{error}") from None
    if not isinstance(result, dict) or result.get("code") != 1:
        message = result.get("msg", "响应格式错误") if isinstance(result, dict) else "响应格式错误"
        raise RuntimeError(f"{method} {path} 业务失败：{message}")
    return result.get("data")


def prepare(args):
    base_url = args.base_url.rstrip("/")
    password = os.environ.get("ADMIN_PASSWORD") or getpass.getpass("请输入商家管理员密码（输入不显示）：")
    login = api(base_url, "POST", "/admin/employee/login", {"username": args.admin, "password": password})
    if not login or not login.get("token"):
        raise RuntimeError("管理员登录响应没有 Token")
    admin_headers = {"token": login["token"]}
    run_id = datetime.now(ZoneInfo("Asia/Shanghai")).strftime("%m%d%H%M%S") + secrets.token_hex(2)
    user_password = os.environ.get("K6_USER_PASSWORD", "K6Coupon123!")

    def create_user(index):
        account = f"k6c_{run_id}_{index:05d}"
        credentials = {"account": account, "password": user_password}
        api(base_url, "POST", "/user/user/register", credentials)
        user = api(base_url, "POST", "/user/user/login", credentials)
        if not user or not user.get("token") or not user.get("id"):
            raise RuntimeError(f"测试账号 {account} 登录后缺少 id 或 Token")
        return {"id": user["id"], "account": account, "token": user["token"]}

    print(f"连接 {base_url}，准备 {args.users} 个独立测试用户（密码哈希会耗时，请等待）……", flush=True)
    with concurrent.futures.ThreadPoolExecutor(max_workers=args.workers) as pool:
        users = list(pool.map(create_user, range(args.users)))

    stocks = {"performance": args.users, "oversell": args.oversell_stock, "single-user": args.single_stock}
    coupons = {}
    now = datetime.now(ZoneInfo("Asia/Shanghai"))
    for test, stock in stocks.items():
        coupons[test] = {}
        for action, coupon_type in (("claim", 2), ("buy", 1)):
            name = f"k6_{run_id}_{test[0]}_{action[0]}"
            api(base_url, "POST", "/admin/coupon/publish", {
                "name": name, "type": coupon_type, "totalStock": stock,
                "discountAmount": 20, "thresholdAmount": 50 if coupon_type == 2 else 0,
                "price": 9.9 if coupon_type == 1 else 0,
                "startTime": (now - timedelta(hours=1)).strftime("%Y-%m-%d %H:%M"),
                "endTime": (now + timedelta(days=1)).strftime("%Y-%m-%d %H:%M"),
                "validTime": 30,
            }, admin_headers)
            # 发布接口没有返回 ID，通过唯一的测试券名查询回填。
            page = api(base_url, "POST", "/admin/coupon/pageQuery",
                       {"page": 1, "pageSize": 100, "name": name}, admin_headers)
            matches = [row for row in (page or {}).get("records", []) if row.get("name") == name]
            if len(matches) != 1:
                raise RuntimeError(f"无法唯一定位刚创建的测试券：{name}")
            coupon_id = matches[0]["id"]
            api(base_url, "PUT", "/admin/coupon/status?" + urlencode({"couponId": coupon_id, "status": 1}),
                headers=admin_headers)
            coupons[test][action] = {"id": coupon_id, "name": name, "stock": stock, "type": coupon_type}
            print(f"  {test:12} {action:5}：ID={coupon_id}，库存={stock}", flush=True)

    output = Path(args.output).resolve()
    output.parent.mkdir(parents=True, exist_ok=True)
    data = {"baseUrl": base_url, "label": args.label, "createdAt": now.isoformat(),
            "adminToken": login["token"], "users": users, "coupons": coupons}
    output.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    output.chmod(0o600)
    print(f"准备完成：{output}\n未支付、未修改已有优惠券，也没有清理已有数据。")
    print("现在可在项目根目录执行：k6 run azure-take-out/azure-server/src/test/k6/coupon/performance.js")
    print("Token 默认有效期为 2 小时。每个写入场景只跑一次；重复测试请重新准备专用券。")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base-url", default=os.environ.get("BASE_URL", "http://localhost:8080"))
    parser.add_argument("--admin", default=os.environ.get("ADMIN_ACCOUNT", "admin"))
    parser.add_argument("--users", type=int, default=1000)
    parser.add_argument("--oversell-stock", type=int, default=100)
    parser.add_argument("--single-stock", type=int, default=1000)
    parser.add_argument("--workers", type=int, default=4)
    parser.add_argument("--label", default="baseline", help="报告标签，例如 baseline 或 cached")
    parser.add_argument("--output", default=str(Path(__file__).parent / "data/local.json"))
    args = parser.parse_args()
    if args.users < 2 or not 0 < args.oversell_stock < args.users or args.single_stock < 1 or args.workers < 1:
        parser.error("users 至少为2；oversell-stock 必须大于0且小于 users；single-stock、workers 必须大于0")
    try:
        prepare(args)
    except (RuntimeError, KeyboardInterrupt) as error:
        print(f"准备未完成：{error}\n已创建的专用账号和券会保留；重新运行会使用新的名称。", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
