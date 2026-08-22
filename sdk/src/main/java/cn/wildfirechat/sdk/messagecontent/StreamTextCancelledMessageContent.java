package cn.wildfirechat.sdk.messagecontent;

import cn.wildfirechat.pojos.MessagePayload;
import cn.wildfirechat.proto.ProtoConstants;

/**
 * 流式文本取消消息内容类
 * <p>
 * 表示AI流式文本生成被取消（生成无产出/失败）的消息。
 * 客户端收到后按 streamId 删除对应的 generating(14)/generated(15) 消息气泡，
 * 取消消息自身不落库、不展示（Transparent），因此无需持久化。
 * 包含取消提示文本和流ID。
 * </p>
 */
public class StreamTextCancelledMessageContent extends MessageContent {
    private String text;
    private String streamId;

    //必须有个空的构造函数
    public StreamTextCancelledMessageContent() {
    }

    public StreamTextCancelledMessageContent(String text, String streamId) {
        this.text = text;
        this.streamId = streamId;
    }

    public StreamTextCancelledMessageContent text(String text) {
        this.text = text;
        return this;
    }

    public StreamTextCancelledMessageContent streamId(String streamId) {
        this.streamId = streamId;
        return this;
    }

    @Override
    public int getContentType() {
        return ProtoConstants.ContentType.StreamingText_Cancelled;
    }

    @Override
    public int getPersistFlag() {
        return ProtoConstants.PersistFlag.Transparent;
    }

    @Override
    public void decode(MessagePayload payload) {
        super.decode(payload);
        this.streamId = payload.getContent();
        this.text = payload.getSearchableContent();
    }

    @Override
    public MessagePayload encode() {
        MessagePayload payload = super.encode();
        payload.setSearchableContent(text);
        payload.setContent(streamId);
        return payload;
    }
}
