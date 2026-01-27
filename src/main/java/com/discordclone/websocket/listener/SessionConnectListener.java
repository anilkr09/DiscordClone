//package com.discordclone.websocket.listener;
//
//import org.springframework.context.ApplicationListener;
//import org.springframework.stereotype.Component;
//import org.springframework.web.socket.messaging.SessionConnectEvent;
//
//@Component
//public class SessionConnectListener
//        implements ApplicationListener<SessionConnectEvent> {
//
//    @Override
//    public void onApplicationEvent(SessionConnectEvent event) {
//        String userId = event.getUser().getName();
//        presenceService.markOnline(userId);
//    }
//}