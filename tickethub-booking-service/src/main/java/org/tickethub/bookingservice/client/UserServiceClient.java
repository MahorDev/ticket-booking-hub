package org.tickethub.bookingservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.tickethub.bookingservice.client.dto.UserDto;

@FeignClient(name = "user-service", url = "${user.service.url:}")
public interface UserServiceClient {

    @GetMapping("/api/users/internal/{userId}")
    UserDto getUserById(@PathVariable("userId") Long userId);
}
