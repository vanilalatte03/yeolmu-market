ALTER TABLE chatmessage
    ADD COLUMN accepted_message_id VARCHAR(36) NULL;

ALTER TABLE chatmessage
    ADD UNIQUE INDEX uk_chatmessage_accepted_message_id (accepted_message_id);

ALTER TABLE chatmessage
    ADD INDEX idx_chatmessage_room_created_id (chatroom_id, created_at, id);
