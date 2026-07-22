package com.forumx.messaging.listener;
import com.forumx.messaging.subscriber.EventSubscriber; import java.util.List; import lombok.RequiredArgsConstructor; import org.springframework.stereotype.Component;
/** Discovers future subscribers; queue declarations are owned by their respective modules. */
@Component @RequiredArgsConstructor
public class RabbitListenerRegistrar { private final List<EventSubscriber> subscribers; public List<EventSubscriber> subscribers() { return List.copyOf(subscribers); } }
