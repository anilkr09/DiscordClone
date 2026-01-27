//package com.discordclone.websocket.listener;
//
//import org.springframework.context.ApplicationListener;
//import org.springframework.stereotype.Component;
//import org.springframework.web.socket.messaging.SessionSubscribeEvent;
//
//@Component
//public class SubscriptionListener
//        implements ApplicationListener<SessionSubscribeEvent> {
//
//    @Override
//    public void onApplicationEvent(SessionSubscribeEvent event) {
//        String destination = event.getMessage()
//                .getHeaders()
//                .get("simpDestination", String.class);
//
//        if (destination.startsWith("/topic/channels/")) {
//            channelService.loadInitialMessages(event.getUser());
//        }
//    }
//}