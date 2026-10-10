package com.sky.web;

import com.sky.support.WebTestSupport;
import org.junit.jupiter.api.Test;
import javax.crypto.Cipher;
import javax.crypto.spec.*;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Exercise the retained callback locally using synthetic AES-GCM data, without contacting WeChat. */
class PayNotifyMvcTest extends WebTestSupport {
    @Test void callbackDecryptsOrderNumberAndRespondsWithSuccess() throws Exception {
        String key="0123456789abcdef0123456789abcdef",nonce="123456789012",associated="test-associated";
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE,new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8),"AES"),new GCMParameterSpec(128,nonce.getBytes(StandardCharsets.UTF_8)));
        cipher.updateAAD(associated.getBytes(StandardCharsets.UTF_8));
        String ciphertext=Base64.getEncoder().encodeToString(cipher.doFinal("{\"out_trade_no\":\"order-9\",\"transaction_id\":\"synthetic-id\"}".getBytes(StandardCharsets.UTF_8)));
        String body="{\"resource\":{\"ciphertext\":\""+ciphertext+"\",\"nonce\":\""+nonce+"\",\"associated_data\":\""+associated+"\"}}";
        mvc.perform(post("/notify/paySuccess").contentType("application/json").content(body)).andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS")).andExpect(jsonPath("$.message").value("SUCCESS"));
        verify(ordersService).paySuccess("order-9");
    }
    @Test void invalidCiphertextDoesNotInvokeOrderService() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> mvc.perform(post("/notify/paySuccess").contentType("application/json")
                .content("{\"resource\":{\"ciphertext\":\"invalid\",\"nonce\":\"123456789012\",\"associated_data\":\"test\"}}")));
        verifyNoInteractions(ordersService);
    }
}
