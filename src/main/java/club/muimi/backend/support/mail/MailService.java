package club.muimi.backend.support.mail;

import club.muimi.backend.common.enums.EmailCodeScene;

public interface MailService {

    void sendVerificationCode(String email, String code, EmailCodeScene scene);
}
