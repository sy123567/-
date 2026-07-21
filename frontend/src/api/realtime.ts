import { useEffect } from "react";
import { Client, type IMessage } from "@stomp/stompjs";
import SockJS from "sockjs-client";
import { apiBase } from "./client";

export type TripEvent = { type: string; data: unknown };

/**
 * 订阅某个行程的实时事件（STOMP over SockJS，后端 /ws + /topic/trips/{id}）。
 * 收到 new-plans / plan-accepted / plan-rejected 等事件时回调 onEvent。
 */
export function useTripRealtime(tripId: number | undefined, onEvent: (event: TripEvent) => void): void {
  useEffect(() => {
    if (tripId === undefined) return;
    const client = new Client({
      webSocketFactory: () => new SockJS(`${apiBase}/ws`),
      reconnectDelay: 4000,
      onConnect: () => {
        client.subscribe(`/topic/trips/${tripId}`, (message: IMessage) => {
          try {
            onEvent(JSON.parse(message.body) as TripEvent);
          } catch {
            // 忽略无法解析的推送消息。
          }
        });
      },
    });
    client.activate();
    return () => {
      void client.deactivate();
    };
  }, [tripId, onEvent]);
}
