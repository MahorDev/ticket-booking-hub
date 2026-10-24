package org.tickethub.paymentservice.dto;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResponseDto {

    private String message;
    private Object data;
    private Boolean error;

}
