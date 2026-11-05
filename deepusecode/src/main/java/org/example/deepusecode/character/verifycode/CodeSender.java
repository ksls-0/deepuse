package org.example.deepusecode.character.verifycode;

/**
 * 验证码发送通道抽象。
 * <p>目前只提供"打日志"的开发实现（{@link EmailCodeSender}、{@link SmsCodeSender}），
 * 接入真实邮件/短信时只需新增一个实现类替换掉即可，业务代码不用改。</p>
 */
public interface CodeSender {

    CodeChannel channel();

    void send(String target, String code);
}
