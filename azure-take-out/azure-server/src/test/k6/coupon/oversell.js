// 每次请求使用不同用户，共抢购一张库存不足的券；成功请求数必须等于初始库存。
import { configure, optionsFor, preflight, runRequest, report } from './common.js';

const config = configure('oversell');
export const options = optionsFor(config);
export function setup() { preflight(config); }
export default function () { runRequest(config); }
export function handleSummary(data) { return report(config, data); }
