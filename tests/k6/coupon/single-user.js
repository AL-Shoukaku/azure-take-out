// 多个 VU 共用同一真实用户的 Token，并发重复提交，预期只有一次成功。
import { configure, optionsFor, preflight, runRequest, report } from './common.js';

const config = configure('single-user');
export const options = optionsFor(config);
export function setup() { preflight(config); }
export default function () { runRequest(config); }
export function handleSummary(data) { return report(config, data); }
