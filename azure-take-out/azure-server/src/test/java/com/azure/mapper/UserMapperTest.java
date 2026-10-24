package com.azure.mapper;

import com.azure.entity.User;
import com.azure.support.SqlTestSupport;
import org.apache.ibatis.exceptions.PersistenceException;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserMapperTest extends SqlTestSupport {
    @Test void accountLookupMapsCredentialsProfileAndTimestamps() {
        User user = mapper(UserMapper.class).getByAccount("user42");
        assertThat(user.getId()).isEqualTo(USER_ID);
        assertThat(user.getPassword()).startsWith("$argon2id$");
        assertThat(user.getUsername()).isEqualTo("测试昵称");
        assertThat(user.getStatus()).isEqualTo(1);
        assertThat(user.getCreateTime()).isNotNull();
        assertThat(user.getUpdateTime()).isEqualTo(user.getCreateTime());
        assertThat(mapper(UserMapper.class).getByAccount("missing")).isNull();
    }

    @Test void accountsAreUniqueButNicknamesCanBeShared() {
        UserMapper users = mapper(UserMapper.class);
        User existing = users.getById(USER_ID);
        User another = User.builder().account("new-account").password(existing.getPassword())
                .username(existing.getUsername()).status(1).build();
        users.save(another);
        assertThat(another.getId()).isNotEqualTo(USER_ID);
        assertThat(users.getByAccount("new-account").getUsername()).isEqualTo(existing.getUsername());

        another.setId(null);
        another.setAccount("user42");
        assertThatThrownBy(() -> users.save(another)).isInstanceOf(PersistenceException.class);
        assertThat(users.getByAccount("user42").getId()).isEqualTo(USER_ID);
    }

    @Test void partialProfileUpdateBindsRootFieldsAndKeepsCredentials() {
        UserMapper users = mapper(UserMapper.class);
        User original = users.getById(USER_ID);
        LocalDateTime now = LocalDateTime.of(2026, 10, 21, 12, 30);
        assertThat(users.updateUser(User.builder().id(USER_ID).username("更新后的昵称").name("李四")
                .phone("13900000042").sex("0").idNumber("123456789012345678")
                .avatar("/images/user/avatar.png").updateTime(now).build())).isEqualTo(1);
        User saved = users.getById(USER_ID);
        assertThat(saved).extracting(User::getUsername, User::getName, User::getPhone, User::getSex,
                        User::getIdNumber, User::getAvatar, User::getUpdateTime)
                .containsExactly("更新后的昵称", "李四", "13900000042", "0", "123456789012345678",
                        "/images/user/avatar.png", now);
        assertThat(saved).extracting(User::getAccount, User::getPassword, User::getStatus, User::getCreateTime)
                .containsExactly(original.getAccount(), original.getPassword(), original.getStatus(), original.getCreateTime());
        assertThat(users.getById(43L).getUsername()).isEqualTo("其他昵称");
    }

    @Test void passwordOnlyUpdateKeepsAllProfileFields() {
        UserMapper users = mapper(UserMapper.class);
        User original = users.getById(USER_ID);
        LocalDateTime now = LocalDateTime.of(2026, 10, 21, 13, 30);
        assertThat(users.updateUser(User.builder().id(USER_ID).password("new-password-hash").updateTime(now).build()))
                .isEqualTo(1);
        User saved = users.getById(USER_ID);
        assertThat(saved.getPassword()).isEqualTo("new-password-hash");
        assertThat(saved.getUpdateTime()).isEqualTo(now);
        assertThat(saved).usingRecursiveComparison().ignoringFields("password", "updateTime").isEqualTo(original);
        assertThat(users.getById(43L).getPassword()).isNotEqualTo("new-password-hash");
    }
}
