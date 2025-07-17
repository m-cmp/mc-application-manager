import request from "../common/request";
import type { EventListener } from "../views/type/type";


// Event Listener 목록
export const getEventListenerList = () => {
  return request.get('/eventlistener/list')
}

// Event Listener 상세
export function getEventListenerDetailInfo(eventlistenerIdx:number) {
  return request.get("/eventlistener/" + eventlistenerIdx);
}

// 중복확인
export function duplicateCheck(param: {eventListenerName:string, eventListenerUrl: string}) {
  return request.get(`/eventlistener/duplicate?eventlistenerName=${param.eventListenerName}&eventListenerUrl=${param.eventListenerUrl}`)
}

// Event Listener 등록
export function registEventListener(param: EventListener) {
  return request.post(`/eventlistener`, param)
}

// Event Listener 수정
export function updateEventListener(param: EventListener) {
  return request.patch(`/eventlistener/${param.id}`, param)
}

// Event Listener 삭제
export function deleteEventListener(eventlistenerIdx: number) {
  return request.delete(`/eventlistener/${eventlistenerIdx}`)
}












