import { getCsrf } from "../../auth/api/owner-access";

export type Condition={type:string;symbol?:string;threshold?:string;adjustmentBasis?:string;shortWindow?:number;longWindow?:number;targetLabel?:string;strategyCode?:string;portfolioId?:string;thresholdPercent?:string;documentType?:string};
export interface Evaluation{id:string;outcome:string;reasonCode:string|null;factAt:string|null;acceptedAt:string|null;evaluatedAt:string;attemptCount:number;evidence:Record<string,unknown>}
export interface Alert{id:string;name:string;condition:Condition;conditionVersion:string;conditionSummary:string;enabled:boolean;episodeState:string;latestEvaluation:Evaluation|null;lastTriggeredAt:string|null;lastDeliveryOutcome:string|null;createdAt:string;updatedAt:string}
export interface Notification{id:string;alertId:string;title:string;message:string;conditionSnapshot:Record<string,unknown>;evidence:Record<string,unknown>;triggeredAt:string;deliveredAt:string;readAt:string|null}
export interface Page<T>{items:T[];totalCount:number;limit:number;offset:number}

export class AlertApiError extends Error{constructor(readonly status:number,readonly reasonCode:string){super(reasonCode)}}

const record=(value:unknown):value is Record<string,unknown>=>typeof value==="object"&&value!==null&&!Array.isArray(value);
const string=(value:unknown):value is string=>typeof value==="string";
const nullableString=(value:unknown):value is string|null=>value===null||string(value);
function invalid():never{throw new AlertApiError(502,"INVALID_SERVER_RESPONSE")}
function object(value:unknown){if(!record(value))invalid();return value}
function condition(value:unknown):Condition{const x=object(value);if(!string(x.type))invalid();return x as Condition}
function evaluation(value:unknown):Evaluation{const x=object(value);if(!string(x.id)||!string(x.outcome)||!nullableString(x.reasonCode)||!nullableString(x.factAt)||!nullableString(x.acceptedAt)||!string(x.evaluatedAt)||typeof x.attemptCount!=="number"||!record(x.evidence))invalid();return x as unknown as Evaluation}
function alert(value:unknown):Alert{const x=object(value);if(!string(x.id)||!string(x.name)||!string(x.conditionVersion)||!string(x.conditionSummary)||typeof x.enabled!=="boolean"||!string(x.episodeState)||!nullableString(x.lastTriggeredAt)||!nullableString(x.lastDeliveryOutcome)||!string(x.createdAt)||!string(x.updatedAt))invalid();condition(x.condition);if(x.latestEvaluation!==null)evaluation(x.latestEvaluation);return x as unknown as Alert}
function notification(value:unknown):Notification{const x=object(value);if(!string(x.id)||!string(x.alertId)||!string(x.title)||!string(x.message)||!record(x.conditionSnapshot)||!record(x.evidence)||!string(x.triggeredAt)||!string(x.deliveredAt)||!nullableString(x.readAt))invalid();return x as unknown as Notification}
function page<T>(value:unknown,decode:(item:unknown)=>T):Page<T>{const x=object(value);if(!Array.isArray(x.items)||typeof x.totalCount!=="number"||typeof x.limit!=="number"||typeof x.offset!=="number")invalid();return{items:x.items.map(decode),totalCount:x.totalCount,limit:x.limit,offset:x.offset}}
function count(value:unknown){const x=object(value);if(typeof x.count!=="number")invalid();return{count:x.count}}
function readAll(value:unknown){const x=object(value);if(typeof x.updatedCount!=="number"||!string(x.readAt))invalid();return{updatedCount:x.updatedCount,readAt:x.readAt}}

async function request<T>(url:string,decode:(value:unknown)=>T,init?:RequestInit):Promise<T>{const r=await fetch(url,{credentials:"same-origin",...init});if(!r.ok){const p=await r.json().catch(()=>({})) as {reasonCode?:unknown};throw new AlertApiError(r.status,string(p.reasonCode)?p.reasonCode:"SERVER_ERROR")}return decode(await r.json())}
async function mutation<T>(url:string,method:string,decode:(value:unknown)=>T,body?:unknown){const csrf=await getCsrf();return request(url,decode,{method,headers:{Accept:"application/json","Content-Type":"application/json",[csrf.headerName]:csrf.token},body:body===undefined?undefined:JSON.stringify(body)})}
async function remove(url:string){const csrf=await getCsrf();const r=await fetch(url,{method:"DELETE",credentials:"same-origin",headers:{Accept:"application/json",[csrf.headerName]:csrf.token}});if(!r.ok){const p=await r.json().catch(()=>({})) as {reasonCode?:unknown};throw new AlertApiError(r.status,string(p.reasonCode)?p.reasonCode:"SERVER_ERROR")}}

export const listAlerts=()=>request("/api/v1/alerts?limit=100&offset=0",x=>page(x,alert));
export const createAlert=(name:string,conditionValue:Condition)=>mutation("/api/v1/alerts","POST",alert,{name,condition:conditionValue});
export const setAlertState=(id:string,enabled:boolean)=>mutation(`/api/v1/alerts/${id}/state`,"PUT",alert,{enabled});
export const deleteAlert=(id:string)=>remove(`/api/v1/alerts/${id}`);
export const listEvaluations=(id:string)=>request(`/api/v1/alerts/${id}/evaluations?limit=20&offset=0`,x=>page(x,evaluation));
export const listNotifications=(unreadOnly=false)=>request(`/api/v1/notifications?limit=100&offset=0&unreadOnly=${unreadOnly}`,x=>page(x,notification));
export const unreadCount=()=>request("/api/v1/notifications/unread-count",count);
export const markRead=(id:string)=>mutation(`/api/v1/notifications/${id}/read`,"PUT",notification);
export const markAllRead=()=>mutation("/api/v1/notifications/read-all","PUT",readAll);
