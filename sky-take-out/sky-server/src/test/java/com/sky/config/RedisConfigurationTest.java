package com.sky.config;

import com.sky.entity.ShoppingCart;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.*;
import java.util.*;
import java.nio.charset.StandardCharsets;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@SuppressWarnings({"rawtypes","unchecked"})
class RedisConfigurationTest {
    @Test void keysStayUtf8StringsAndValuesRetainExistingJdkSerialization() {
        RedisConnectionFactory factory = mock(RedisConnectionFactory.class);
        RedisTemplate template = new RedisConfiguration().redisTemplate(factory); template.afterPropertiesSet();
        assertThat(template.getConnectionFactory()).isSameAs(factory);
        assertThat(template.getKeySerializer()).isInstanceOf(StringRedisSerializer.class);
        assertThat(template.getKeySerializer().serialize("dish_3")).isEqualTo("dish_3".getBytes(StandardCharsets.UTF_8));
        assertThat(template.getValueSerializer()).isInstanceOf(JdkSerializationRedisSerializer.class);
        ShoppingCart item = ShoppingCart.builder().dishId(7L).number(2).build(); List<ShoppingCart> input = Collections.singletonList(item);
        assertThat(template.getValueSerializer().deserialize(template.getValueSerializer().serialize(input))).isEqualTo(input);
        verifyNoInteractions(factory);
    }
}
