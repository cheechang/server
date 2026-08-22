package cn.wildfirechat.sdk.messagecontent;

import cn.wildfirechat.pojos.Conversation;
import cn.wildfirechat.pojos.MessagePayload;
import cn.wildfirechat.proto.WFCMessage;
import cn.wildfirechat.sdk.model.Message;
import cn.wildfirechat.sdk.utilities.ClassUtil;

import java.io.IOException;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 消息内容工厂类
 * <p>
 * 负责消息内容的编码和解码，维护消息类型到消息内容类的映射关系。
 * 支持内置消息类型和自定义消息类型的注册。
 * </p>
 */
public class MessageContentFactory {
    private static final Map<Integer, Class<? extends MessageContent>> contentClassMap = new ConcurrentHashMap<>();

    private static List<Class> buildinMessageContents = Arrays.asList(
        MeetingMinutesMessageContent.class,
        TranscriptionMessageContent.class,
        ArticlesMessageContent.class,
        StreamTextGeneratedMessageContent.class,
        CallStartMessageContent.class,
        StreamTextGeneratingMessageContent.class,
        StreamTextCancelledMessageContent.class,
        CardMessageContent.class,
        MultiCallOngoingMessageContent.class,
        TextMessageContent.class,
        DeleteMessageContent.class,
        NotDeliveredMessageContent.class,
        TipNotificationMessageContent.class,
        FileMessageContent.class,
        PTTSoundMessageContent.class,
        TypingMessageContent.class,
        ImageMessageContent.class,
        RecallMessageContent.class,
        UnknownMessageContent.class,
        LinkMessageContent.class,
        RichNotificationMessageContent.class,
        VideoMessageContent.class,
        LocationMessageContent.class,
        SoundMessageContent.class,
        StickerMessageContent.class
    );

    static  {
        registerAllMessageContent();
    }

    public static Message decodeMessage(WFCMessage.Message protoMessage) {
        Message message = new Message();
        message.content = decodeMessageContent(protoMessage.getContent());
        WFCMessage.Conversation protoConversation = protoMessage.getConversation();
        message.conversation = new Conversation(protoConversation.getType(), protoConversation.getTarget(), protoConversation.getLine());
        message.messageUid = protoMessage.getMessageId();
        message.sender = protoMessage.getFromUser();
        message.serverTime = protoMessage.getServerTimestamp();
        message.toUsers = protoMessage.getToList();
        return message;
    }

    public static MessageContent decodeMessageContent(WFCMessage.MessageContent protoMessageContent) {
        MessagePayload payload = MessagePayload.fromProtoMessageContent(protoMessageContent);
        return decodeMessageContent(payload);
    }

    public static MessageContent decodeMessageContent(MessagePayload messagePayload) {
        Class<? extends MessageContent> cls = contentClassMap.get(messagePayload.getType());
        MessageContent messageContent;
        if(cls != null) {
            try {
                messageContent = cls.newInstance();
            } catch (InstantiationException e) {
                throw new RuntimeException(e);
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        } else {
            messageContent = new UnknownMessageContent();
        }
        messageContent.decode(messagePayload);
        return messageContent;
    }

    public static void registerCustomMessageContent(Class<? extends MessageContent> cls) throws Exception {
        MessageContent content = cls.newInstance();
        contentClassMap.put(content.getContentType(), cls);
    }

    private static void registerAllMessageContent() {
        try {
            for (Class buildinMessageContent : buildinMessageContents) {
                MessageContent content = (MessageContent)buildinMessageContent.newInstance();
                contentClassMap.put(content.getContentType(), buildinMessageContent);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        try {
            for (Class cls : ClassUtil.getAllAssignedClass(MessageContent.class)) {
                if(!Modifier.isAbstract(cls.getModifiers())) {
                    try {
                        MessageContent content = (MessageContent)cls.newInstance();
                        contentClassMap.put(content.getContentType(), cls);
                    } catch (InstantiationException e) {
                        e.printStackTrace();
                    } catch (IllegalAccessException e) {
                        e.printStackTrace();
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
    }
}
