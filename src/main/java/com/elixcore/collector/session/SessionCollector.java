package com.elixcore.collector.session;

import com.elixcore.lib.collector.EndpointCollector;
import com.elixcore.lib.protocol.message.ElixcoreProtocol;
import com.elixcore.lib.protocol.message.payload.collect.state.PayloadState;
import com.google.protobuf.Any;
import com.google.protobuf.GeneratedMessage;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.stream.Collectors;

@Slf4j
public class SessionCollector extends EndpointCollector {
    private static Map<String, SessionCollectedData> preSessionMap = new ConcurrentHashMap<>();
    private static Map<String, SessionCollectedData> listenMap     = new ConcurrentHashMap<>();

    public PayloadState.StateBundle collectSession() {
        long now = System.currentTimeMillis();

        ProcessBuilder ssTcp = new ProcessBuilder("sh", "-c", "ss -aiOpntH");
        ProcessBuilder ssUdp = new ProcessBuilder("sh", "-c", "ss -aiOpnuH");
        ssTcp.redirectErrorStream(true);
        ssUdp.redirectErrorStream(true);
        // 버퍼 크기를 16KB로 설정
        int bufferSize = 16 * 1024;
        Queue<String> tcpLineQ = new ConcurrentLinkedQueue<>();
        Queue<String> udpLineQ = new ConcurrentLinkedQueue<>();
        try {
            Process ssTcpProc = ssTcp.start();
            Process ssUdpProc = ssUdp.start();

            BufferedReader reader = new BufferedReader(new InputStreamReader(ssTcpProc.getInputStream()), bufferSize);
            String line;
            try {
                while ((line = reader.readLine()) != null) {
                    tcpLineQ.add(line);
                    //                    System.out.println(line);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

            BufferedReader udpReader = new BufferedReader(new InputStreamReader(ssUdpProc.getInputStream()), bufferSize);
            String udpLine;
            try {
                while ((udpLine = udpReader.readLine()) != null) {
                    udpLineQ.add(udpLine);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            ssUdpProc.waitFor();
            ssUdpProc.destroy();
            ssTcpProc.waitFor();
            ssTcpProc.destroy();
        } catch (Exception e) {
            log.error("SS Command Fail : {}", e.getMessage());
        }

        Set<Integer> listenPortSet = new CopyOnWriteArraySet<>();

        List<SessionCollectedData> tcpCollectedDataList =
            tcpLineQ.stream()
                    .parallel()
                    .map(line -> {
                        SessionCollectedData sessionCollectedData = new SessionCollectedData(1);
                        String[] split = line.split("\\s+");
                        String state = split[0];
                        PayloadState.SessionState stateEnum = this.fromStateStr(state);
                        String local = split[3];
                        String peer = split[4];
                        Long rxByte = 0L;
                        Long txByte = 0L;
                        Long rxPacket = 0L;
                        Long txPacket = 0L;

                        sessionCollectedData.setLocal(Optional.ofNullable(local)
                                                              .map(address -> SessionAddress.findMatch(address))
                                                              .orElse(null));
                        sessionCollectedData.setPeer(Optional.ofNullable(peer)
                                                             .map(address -> SessionAddress.findMatch(address))
                                                             .orElse(null));

                        sessionCollectedData.setState(stateEnum);
                        sessionCollectedData.setPid(0);

                        sessionCollectedData.setPid(Optional.ofNullable(extractPidWithIndex(line, "pid="))
                                                            .map(Integer::parseInt)
                                                            .orElse(0));
                        txByte   = Optional.ofNullable(extractPidWithIndex(line, "bytes_sent:"))
                                           .map(Long::parseLong)
                                           .orElse(0L);
                        rxByte   = Optional.ofNullable(extractPidWithIndex(line, "bytes_received:"))
                                           .map(Long::parseLong)
                                           .orElse(0L);
                        txPacket = Optional.ofNullable(extractPidWithIndex(line, "segs_out:"))
                                           .map(Long::parseLong)
                                           .orElse(0L);
                        rxPacket = Optional.ofNullable(extractPidWithIndex(line, "segs_in:"))
                                           .map(Long::parseLong)
                                           .orElse(0L);

                        SessionCollectedData preSession
                            = preSessionMap.get(sessionCollectedData.getInterlockId());

                        if (preSession != null) {
                            long preRxByte = preSession.getRxByte();
                            long preTxByte = preSession.getTxByte();
                            long preRxPacket = preSession.getRxPacket();
                            long preTxPacket = preSession.getTxPacket();

                            rxByte   = rxByte < preRxByte ? rxByte : rxByte - preRxByte;
                            txByte   = txByte < preTxByte ? txByte : txByte - preTxByte;
                            rxPacket = rxPacket < preRxPacket ? rxPacket : rxPacket - preRxPacket;
                            txPacket = txPacket < preTxPacket ? txPacket : txPacket - preTxPacket;
                        }

                        if (stateEnum == PayloadState.SessionState.LISTEN) {
                            Optional.ofNullable(sessionCollectedData.getLocal())
                                    .map(SessionAddress::getPort)
                                    .ifPresent(listenPortSet::add);
                        } else {
                            sessionCollectedData.setRxByte(rxByte);
                            sessionCollectedData.setTxByte(txByte);
                            sessionCollectedData.setRxPacket(rxPacket);
                            sessionCollectedData.setTxPacket(txPacket);
                        }

                        return sessionCollectedData;
                    })
                    .collect(Collectors.toList());

        Map<Integer, List<SessionCollectedData>> pidSessionMap =
            tcpCollectedDataList.stream()
                                .collect(Collectors.groupingBy(SessionCollectedData::getPid));


        /* Convert Protobuf */

        PayloadState.StateBundle.Builder sessionBundleBuilder = PayloadState.StateBundle.newBuilder();
        sessionBundleBuilder.setCollectTime(now);

        pidSessionMap.entrySet()
                     .forEach(entry -> {
                         Integer pid = entry.getKey();
                         List<SessionCollectedData> sessionList = entry.getValue();
                         PayloadState.SessionProcess.Builder pidSessionBuilder = PayloadState.SessionProcess.newBuilder();

                         pidSessionBuilder.setPid(pid);

                         Map<PayloadState.SessionState, List<PayloadState.Session>> inSessionStateMap = new HashMap<>();
                         Map<PayloadState.SessionState, List<PayloadState.Session>> outSessionStateMap = new HashMap<>();

                         for (SessionCollectedData session : sessionList) {
                             PayloadState.Session.Builder sessionBuilder = PayloadState.Session.newBuilder();
                             PayloadState.SessionState state = session.getState();
                             /*Local Address*/
                             SessionAddress local = session.getLocal();
                             boolean listen = false;
                             boolean in = false;

                             Integer localPort = local.getPort();
                             if (state == PayloadState.SessionState.LISTEN) {
                                 listen = true;
                                 in     = true;
                             } else {
                                 if (listenPortSet.contains(localPort)) {
                                     in = true;
                                 }
                             }

                             PayloadState.SessionAddress.Builder localAddressBuilder = PayloadState.SessionAddress.newBuilder();
                             localAddressBuilder.setAddress(local.getAddress());
                             localAddressBuilder.setPort(localPort);
                             PayloadState.SessionAddress localAddress = localAddressBuilder.build();
                             if (in) {
                                 sessionBuilder.setDst(localAddress);
                             } else {
                                 sessionBuilder.setSrc(localAddress);
                             }

                             /*Peer Address*/
                             SessionAddress peer = session.getPeer();
                             if (peer != null) {
                                 PayloadState.SessionAddress.Builder peerAddressBuilder = PayloadState.SessionAddress.newBuilder();
                                 peerAddressBuilder.setAddress(peer.getAddress());
                                 Integer port = peer.getPort();
                                 if (port != null) {
                                     peerAddressBuilder.setPort(port);
                                 }
                                 PayloadState.SessionAddress peerAddress = peerAddressBuilder.build();
                                 if (in) {
                                     sessionBuilder.setSrc(peerAddress);
                                 } else {
                                     sessionBuilder.setDst(peerAddress);
                                 }
                             }

                             switch (state) {
                                 case LISTEN: {
                                     break;
                                 }
                                 default: {
                                     /*Usage*/
                                     sessionBuilder.setRxByte(session.getRxByte());
                                     sessionBuilder.setTxByte(session.getTxByte());
                                     sessionBuilder.setRxPacket(session.getRxPacket());
                                     sessionBuilder.setTxPacket(session.getTxPacket());
                                     break;
                                 }
                             }

                             if (state == PayloadState.SessionState.LISTEN) {
                                 pidSessionBuilder.addListen(sessionBuilder.build());
                             } else {
                                 Optional.ofNullable(local)
                                         .map(SessionAddress::getPort)
                                         .ifPresent(port -> {
                                             if (listenPortSet.contains(port)) {
                                                 List<PayloadState.Session> stateSessionList = inSessionStateMap.computeIfAbsent(state, s -> new ArrayList<>());
                                                 stateSessionList.add(sessionBuilder.build());
                                             } else {
                                                 List<PayloadState.Session> stateSessionList = outSessionStateMap.computeIfAbsent(state,
                                                                                                                                  s -> new ArrayList<>()
                                                 );
                                                 stateSessionList.add(sessionBuilder.build());
                                             }
                                         });
                             }
                         }

                         /*IN SESSION*/
                         if (inSessionStateMap.size() > 0) {
                             PayloadState.SessionStateUsage.Builder inUsageBuilder = PayloadState.SessionStateUsage.newBuilder();
                             inSessionStateMap.forEach((state, stateSessionList) -> {
                                 inUsageBuilder.setState(state);
                                 inUsageBuilder.addAllSession(stateSessionList);
                                 pidSessionBuilder.addIn(inUsageBuilder.build());
                             });
                         }

                         /*OUT SESSION*/
                         if (outSessionStateMap.size() > 0) {
                             PayloadState.SessionStateUsage.Builder outUsageBuilder = PayloadState.SessionStateUsage.newBuilder();
                             outSessionStateMap.forEach((state, stateSessionList) -> {
                                 outUsageBuilder.setState(state);
                                 outUsageBuilder.addAllSession(stateSessionList);
                                 pidSessionBuilder.addOut(outUsageBuilder.build());
                             });
                         }

                         sessionBundleBuilder.addData(Any.pack(pidSessionBuilder.build()));
                     });
        preSessionMap.clear();
        listenMap.clear();
        tcpCollectedDataList.parallelStream()
                            .forEach(session -> {
                                if (session.getState() == PayloadState.SessionState.LISTEN) {
                                    listenMap.put(session.getInterlockId(), session);
                                } else {
                                    preSessionMap.put(session.getInterlockId(), session);
                                }
                            });

        log.debug("session count : {}, listen count : {}", preSessionMap.size(), listenMap.size());

        return sessionBundleBuilder.build();
    }

    private String extractPidWithIndex(String input,
                                       String findKey) {
        int pidIndex = input.indexOf(findKey);
        if (pidIndex != -1) {
            int startIndex = pidIndex + findKey.length();
            int endIndex = startIndex;
            while (endIndex < input.length() && Character.isDigit(input.charAt(endIndex))) {
                endIndex++;
            }
            return input.substring(startIndex, endIndex);
        }
        return null;
    }

    public static PayloadState.SessionState fromStateStr(String s) {
        switch (s.toLowerCase()) {
            case "estab":
                return PayloadState.SessionState.ESTABLISHED;
            case "syn-sent":
                return PayloadState.SessionState.SYN_SENT;
            case "syn-recv":
                return PayloadState.SessionState.SYN_RECV;
            case "fin-wait-1":
                return PayloadState.SessionState.FIN_WAIT_1;
            case "fin-wait-2":
                return PayloadState.SessionState.FIN_WAIT_2;
            case "time-wait":
                return PayloadState.SessionState.TIME_WAIT;
            case "close":
                return PayloadState.SessionState.CLOSED;
            case "close-wait":
                return PayloadState.SessionState.CLOSE_WAIT;
            case "last-ack":
                return PayloadState.SessionState.LAST_ACK;
            case "listen":
                return PayloadState.SessionState.LISTEN;
            case "closing":
                return PayloadState.SessionState.CLOSING;
            case "UNCONN":
                return PayloadState.SessionState.UNCONN;
            case "NONE":
            default:
                return PayloadState.SessionState.UNRECOGNIZED;
        }
    }

    @Override
    public GeneratedMessage collect() {
        return collectSession();
    }

    @Override
    public ElixcoreProtocol.DataType getDataType() {
        return ElixcoreProtocol.DataType.SESSION;
    }
}
