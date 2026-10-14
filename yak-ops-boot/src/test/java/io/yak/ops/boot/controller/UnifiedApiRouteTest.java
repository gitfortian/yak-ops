package io.yak.ops.boot.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.common.bean.dto.security.account.AccountLoginDTO;
import io.yak.ops.security.web.PublicEndpoint;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RequestMapping;

/** Verifies that authentication and user administration use the application API namespace. */
class UnifiedApiRouteTest {

    @Test
    void accountAndUserRoutesUseApplicationPrefix() {
        String account = LoginController.class.getAnnotation(RequestMapping.class).value()[0];
        String user = UserController.class.getAnnotation(RequestMapping.class).value()[0];
        assertEquals("/api/v1/account", account);
        assertEquals("/api/v1/user", user);
    }

    @Test
    void loginRemainsPublic() throws NoSuchMethodException {
        var method = LoginController.class.getMethod("login", HttpServletRequest.class, AccountLoginDTO.class);
        assertTrue(method.isAnnotationPresent(PublicEndpoint.class));
    }
}
