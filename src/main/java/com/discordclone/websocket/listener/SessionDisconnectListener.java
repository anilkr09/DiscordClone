//package com.discordclone.websocket.listener;
//
//import org.springframework.context.ApplicationListener;
//import org.springframework.stereotype.Component;
//import org.springframework.web.socket.messaging.SessionDisconnectEvent;
//
//@Component
//public class SessionDisconnectListener
//        implements ApplicationListener<SessionDisconnectEvent> {
//
//    @Override
//    public void onApplicationEvent(SessionDisconnectEvent event) {
//        String userId = event.getUser().getName();
//        presenceService.markOffline(userId);
//    }
//}