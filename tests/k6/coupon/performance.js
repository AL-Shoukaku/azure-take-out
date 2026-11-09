// 默认完整流程：同一用户查列表→领取满减券→创建代金券订单，同时统计流程总耗时与各接口耗时。
// ACTION=list/claim/buy 仍可单独测量某个接口。
import { configure, optionsFor, preflight, runRequest, report } from './common.js';

const config = configure('performance');
export const options = optionsFor(config);
export function setup() { preflight(config); }
export default function () { runRequest(config); }
export function handleSummary(data) { return report(config, data); }
