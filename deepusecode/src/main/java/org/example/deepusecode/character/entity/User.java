package org.example.deepusecode.character.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 用户。原先是 MyBatis 映射，现已转为 JPA 实体，
 * 好处是 ddl-auto 能自动建表，不再需要手工维护建表脚本。
 * <p>表名为 app_user 而不是 user：user 在 H2 / PostgreSQL 是保留字，
 * 用保留字做表名会在换库时直接建表失败。</p>
 */
@Entity
@Table(name = "app_user")
@Getter
@Setter
@NoArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 100)
    private String phone;

    @Column(length = 150)
    private String email;

    @Column(name = "account", nullable = false, length = 150)
    private String account;

    @Column(name = "password", nullable = false, length = 100)
    private String password;

    @Column(name = "create_time", updatable = false)
    private LocalDateTime createTime;

    @PrePersist
    void prePersist() {
        if (createTime == null) {
            createTime = LocalDateTime.now();
        }
    }
}
