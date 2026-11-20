import { configurePurchase, optionsFor, preflight, runRequest, report } from './common.js';

const config = configurePurchase('buy-orders');
export const options = optionsFor(config);
export function setup() { preflight(config); }
export default function () { runRequest(config); }
export function handleSummary(data) { return report(config, data); }
