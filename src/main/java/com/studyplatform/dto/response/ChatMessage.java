package com.studyplatform.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessage {
    public ChatMessage(String nickname, String text, long timestamp, String emoji) {
        this.nickname = nickname;
        this.text = text;
        this.timestamp = timestamp;
        this.emoji = emoji;
        this.type = "TEXT";
    }

    private String nickname;
    private String text;
    private long timestamp;
    private String emoji;
    private String type = "TEXT";
    private String imageUrl;
    private String fileName;
    private Long fileSize;
    private String mentionedNickname; // set when message starts with @nickname
    private boolean voiceRequested;
    private String voiceText;
    private long id;
    private Long replyToId;
    private String replyToNickname;
    private String replyToText;
}
