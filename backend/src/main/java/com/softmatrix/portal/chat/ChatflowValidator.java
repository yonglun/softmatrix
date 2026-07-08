package com.softmatrix.portal.chat;

public interface ChatflowValidator {
    /** Chatflow 是否存在于 Flowise。 */
    boolean chatflowExists(String chatflowId);
}
