package com.elixcore.collector.session;

import com.elixcore.lib.protocol.message.payload.collect.state.PayloadState;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;

@Data
@RequiredArgsConstructor
@AllArgsConstructor
public class SessionCollectedData {
    private final int                       protocol; /* TCP: 1, UDP: 2*/
    private       PayloadState.SessionState state;
    private       long                      rxByte;
    private       long                      txByte;
    private       long                      rxPacket;
    private       long                      txPacket;
    private       SessionAddress            local;
    private       SessionAddress            peer;
    private       Integer                   pid;
    private       int                       direction; /*IN :1, OUT :2*/

    public String getInterlockId() {
        return String.format("%s-%s-%s-%s-%s", local != null ? local : "NONE", peer != null ? peer : "NONE", state, protocol, pid != null ? pid : "NONE");
    }
}
