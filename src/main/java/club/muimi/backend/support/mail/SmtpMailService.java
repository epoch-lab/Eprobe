package club.muimi.backend.support.mail;

import club.muimi.backend.common.api.ErrorCode;
import club.muimi.backend.common.enums.EmailCodeScene;
import club.muimi.backend.config.BrandProperties;
import club.muimi.backend.config.SmtpProperties;
import club.muimi.backend.exception.BusinessException;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
public class SmtpMailService implements MailService {

    private final JavaMailSender javaMailSender;
    private final SmtpProperties smtpProperties;
    private final BrandProperties brandProperties;

    public SmtpMailService(JavaMailSender javaMailSender, SmtpProperties smtpProperties, BrandProperties brandProperties) {
        this.javaMailSender = javaMailSender;
        this.smtpProperties = smtpProperties;
        this.brandProperties = brandProperties;
    }

    @Override
    public void sendVerificationCode(String email, String code, EmailCodeScene scene) {
        try {
            MimeMessage mimeMessage = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
            helper.setFrom(smtpProperties.getUsername());
            helper.setTo(email);
            helper.setSubject(buildSubject(scene));
            helper.setText(buildBody(code, scene), true);
            javaMailSender.send(mimeMessage);
        } catch (MessagingException | MailException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "验证码邮件发送失败，请稍后再试");
        }
    }

    private String buildSubject(EmailCodeScene scene) {
        return switch (scene) {
            case REGISTER -> "%s 注册验证码".formatted(brandProperties.getName());
            case RESET_PASSWORD -> "%s 密码重置验证码".formatted(brandProperties.getName());
        };
    }

    private String buildBody(String code, EmailCodeScene scene) {
        String sceneText = switch (scene) {
            case REGISTER -> "注册";
            case RESET_PASSWORD -> "重置密码";
        };

        String safeName = escapeHtml(brandProperties.getName());
        String safeScene = escapeHtml(sceneText);
        String safeCode = escapeHtml(code);

        return """
                <div style="max-width:480px;margin:0 auto;padding:40px 20px;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif;color:#1a1a1a;">
                    <div style="text-align:center;">
                        <div style="font-size:18px;font-weight:600;letter-spacing:2px;color:#4a4a4a;margin-bottom:32px;">
                            %s
                        </div>
                        <div style="font-size:15px;color:#6b6b6b;margin-bottom:28px;line-height:1.6;">
                            您正在进行 <span style="color:#2c2c2c;font-weight:500;">「%s」</span>
                        </div>
                        <div style="background:#f7f8fa;border-radius:12px;padding:28px 20px;margin-bottom:32px;display:inline-block;min-width:200px;">
                            <div style="font-size:32px;font-weight:600;letter-spacing:6px;color:#1e1e1e;font-family:'SF Mono','Cascadia Code','Courier New',monospace;">
                                %s
                            </div>
                        </div>
                        <div style="font-size:13px;color:#9b9b9b;line-height:1.8;">
                            <span style="display:block;">· 验证码 5 分钟内有效，请勿泄露</span>
                            <span style="display:block;">· 如非本人操作，请忽略本邮件</span>
                        </div>
                    </div>
                </div>
                """.formatted(safeName, safeScene, safeCode);
    }

    private String escapeHtml(String input) {
        if (input == null) return "";
        return input
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}