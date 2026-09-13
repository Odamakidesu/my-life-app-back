package com.mylifeapp.auth.jwt;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * JWT の設定。
 *
 * <p>以前は {@code @Value("${jwt.expiration:86400000}")} がコード側のキー名、
 * {@code jwt.expiration-ms} が properties 側のキー名になっており、設定が黙って無視されていた。
 * 既定値が同値だったため誰も気づけない状態だった。
 * ここに集約して既定値を持たせないことで、キー名のズレは起動時のバインド失敗として露見する。
 */
@ConfigurationProperties(prefix = "jwt")
@Validated
public record JwtProperties(
        @NotBlank(message = "jwt.secret が未設定です。ローカルは application-local.properties、本番は ECS の JWT_SECRET で注入してください")
        String secret,

        @Min(value = 60_000, message = "jwt.expiration-ms は 60000（1分）以上にしてください")
        long expirationMs
) {
}
