package com.tickethub.event.util;

import com.tickethub.event.exception.BusinessException;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class PageableValidator {

    public void validate(Pageable p, Set<String> allowed) {
        p.getSort().forEach(o -> {
            if (!allowed.contains(o.getProperty()))
                throw new BusinessException("Unsupported sort field: " + o.getProperty());
        });
    }
}
