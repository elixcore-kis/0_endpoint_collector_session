package com.elixcore.collector.session;

import lombok.*;
import lombok.extern.slf4j.Slf4j;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.elixcore.collector.session.SessionAddress.PatternName.*;

@Slf4j
@Getter
@Setter
@EqualsAndHashCode(callSuper = false)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SessionAddress {

    /**
     * ex : 32313
     */
    private static final String                    portNumberRegex              = "(?<port>\\d{1,5}|\\*)";
    /**
     * ex : %lo
     */
    private static final String                    loopbackRegex                = "(?<lo>%lo)";
    /**
     * ex :
     * ff06:0:0:0:0:0:0:c3,
     * ffff:ffff:ffff:ffff:ffff:ffff:ffff:ffff
     */
    private static final String                    ipv6Regex                    = "([0-9a-f]{1,4}:){7}([0-9a-f]){1,4}";
    private static final String                    ipv6AddressRegex             = String.format("(?<address>%s)", ipv6Regex);
    /**
     * ex :
     * ff06:0:0:0:0:0:0:c3:32312,
     * ffff:ffff:ffff:ffff:ffff:ffff:ffff:ffff:32123
     */
    private static final String                    ipv6PortRegex                = String.format("%s:%s", ipv6AddressRegex, portNumberRegex);
    /**
     * ex :
     * [ff06:0:0:0:0:0:0:c3]:32312,
     * [ffff:ffff:ffff:ffff:ffff:ffff:ffff:ffff]:32123
     */
    private static final String                    ipv6BracketPortRegex         = String.format("\\[%s]:%s", ipv6AddressRegex, portNumberRegex);
    /**
     * ex :
     * ff32::43:f3
     * f:ff::f:0
     * ff06::c3
     * ::FFFF
     */
    private static final String
                                                   ipv6SimpleRegex
                                                                                = "((?:[0-9A-Fa-f]{1,4}(?::[0-9A-Fa-f]{1,4})*)?)::((?:[0-9A-Fa-f]{1,4}(?::[0-9A-Fa-f]{1,4})*)?)|\\*|::";
    private static final String                    ipv6SimpleAddressRegex       = String.format("(?<address>%s)", ipv6SimpleRegex);
//    /**
//     * ex :
//     * ff32::43:f3:32323
//     * f:ff::f:0:32323
//     * ff06::c3:32323
//     * ::FFFF:32323
//     */
//    private static final String                    ipv6SimplePortRegex          = "%s:%s".formatted(ipv6SimpleRegex, portNumberRegex);
    /**
     * ex :
     * [ff32::43:f3]:32323
     * [f:ff::f:0]:32323
     * [ff06::c3]:32323
     * [::FFFF]:32323
     */
    private static final String                    ipv6SimpleBracketPortRegex   = String.format("\\[%s]:%s", ipv6SimpleAddressRegex, portNumberRegex);
    /**
     * ex : 255.255.255.255
     */
    private static final String
                                                   ipv4Regex
                                                                                = "((25[0-5]|2[0-4][0-9]|1[0-9][0-9]|[1-9][0-9]|[0-9])(\\.(?!$)|$)){3}(25[0-5]|2[0-4][0-9]|1[0-9][0-9]|[1-9][0-9]|[0-9])|\\*";
    private static final String                    ipv4AddressRegex             = String.format("(?<address>%s)", ipv4Regex);
    /**
     * ex : 12.3.45.5:44832
     */
    private static final String                    ipv4PortRegex                = String.format("%s:%s", ipv4AddressRegex, portNumberRegex);
    /**
     * ex : 12.3.45.5%lo:44832
     */
    private static final String                    ipv4LoopbackPortRegex        = String.format("%s%s:%s", ipv4AddressRegex, loopbackRegex, portNumberRegex);
    /**
     * ex : ::FFFF:172.16.0.15
     */
    private static final String                    ipv4MappedV6Regex            = String.format("(?<address>(%s):(?<ipv4>%s))", ipv6SimpleRegex, ipv4Regex);
    /**
     * ex : [::FFFF:172.16.0.15]
     */
    private static final String                    ipv4MappedV6BracketRegex     = String.format("\\[%s]", ipv4MappedV6Regex);
    /**
     * ex : ::FFFF:172.16.0.15:33231
     */
    private static final String                    ipv4MappedV6BracketPortRegex = String.format("%s:%s", ipv4MappedV6BracketRegex, portNumberRegex);
    /**
     * ex : [::ffff:172.16.0.15]:54946
     */
    private static final String                    ipv4MappedV6PortRegex        = String.format("%s:%s", ipv4MappedV6Regex, portNumberRegex);
    private static final Map<PatternName, Pattern> addressPatternMap            = new LinkedHashMap<>();

    static {
        Pattern P_IPV4_MAPPED_V6_BRACKET = Pattern.compile(toOnlyRegex(ipv4MappedV6BracketRegex));
//        System.out.println("P_IPV4_MAPPED_V6_BRACKET : " + toOnlyRegex(ipv4MappedV6BracketRegex));
        Pattern P_IPV4_MAPPED_V6 = Pattern.compile(toOnlyRegex(ipv4MappedV6Regex));
//        System.out.println("P_IPV4_MAPPED_V6 : " + toOnlyRegex(ipv4MappedV6Regex));
        Pattern P_IPV4_MAPPED_V6_BRACKET_PORT = Pattern.compile(toOnlyRegex(ipv4MappedV6BracketPortRegex));
//        System.out.println("P_IPV4_MAPPED_V6_BRACKET_PORT : " + toOnlyRegex(ipv4MappedV6BracketPortRegex));
        Pattern P_IPV4_MAPPED_V6_PORT = Pattern.compile(toOnlyRegex(ipv4MappedV6PortRegex));
//        System.out.println("P_IPV4_MAPPED_V6_PORT : " + toOnlyRegex(ipv4MappedV6PortRegex));
        Pattern P_IPV4_PORT = Pattern.compile(toOnlyRegex(ipv4PortRegex));
//        System.out.println("P_IPV4_PORT : " + toOnlyRegex(ipv4PortRegex));
        Pattern P_IPV4 = Pattern.compile(toOnlyRegex(ipv4AddressRegex));
//        System.out.println("P_IPV4 : " + toOnlyRegex(ipv4AddressRegex));
        Pattern P_IPV6 = Pattern.compile(toOnlyRegex(ipv6AddressRegex));
//        System.out.println("P_IPV6 : " + toOnlyRegex(ipv6AddressRegex));
        Pattern P_IPV6_SIMPLE = Pattern.compile(toOnlyRegex(ipv6SimpleAddressRegex));
//        System.out.println("P_IPV6_SIMPLE : " + toOnlyRegex(ipv6SimpleAddressRegex));
        Pattern P_IPV4_LOOPBACK_LESSON_PORT = Pattern.compile(toOnlyRegex(ipv4LoopbackPortRegex));
//        System.out.println("P_IPV4_LOOPBACK_LESSON_PORT : " + toOnlyRegex(ipv4LoopbackPortRegex));

        Pattern P_IPV6_PORT = Pattern.compile(toOnlyRegex(ipv6PortRegex));
//        System.out.println("P_IPV6_PORT : " + toOnlyRegex(ipv6PortRegex));
        Pattern P_IPV6_BRACKET_PORT = Pattern.compile(toOnlyRegex(ipv6BracketPortRegex));
//        System.out.println("P_IPV6_BRACKET_PORT : " + toOnlyRegex(ipv6BracketPortRegex));
        Pattern P_IPV6_SIMPLE_BRACKET_PORT = Pattern.compile(toOnlyRegex(ipv6SimpleBracketPortRegex));
//        System.out.println("P_IPV6_SIMPLE_BRACKET_PORT : " + toOnlyRegex(ipv6SimpleBracketPortRegex));

        // 순서대로 검사
        addressPatternMap.put(IPV4_MAPPED_V6_BRACKET, P_IPV4_MAPPED_V6_BRACKET);
        addressPatternMap.put(IPV6_BRACKET_PORT, P_IPV6_BRACKET_PORT);
        addressPatternMap.put(IPV4_MAPPED_V6, P_IPV4_MAPPED_V6);
        addressPatternMap.put(IPV4_MAPPED_V6_BRACKET_PORT, P_IPV4_MAPPED_V6_BRACKET_PORT);
        addressPatternMap.put(IPV4_MAPPED_V6_PORT, P_IPV4_MAPPED_V6_PORT);
        addressPatternMap.put(IPV4_PORT, P_IPV4_PORT);
        addressPatternMap.put(IPV6_SIMPLE, P_IPV6_SIMPLE);
        addressPatternMap.put(IPV6, P_IPV6);
        addressPatternMap.put(IPV4, P_IPV4);
        addressPatternMap.put(IPV4_LOOPBACK_PORT, P_IPV4_LOOPBACK_LESSON_PORT);
        addressPatternMap.put(IPV6_PORT, P_IPV6_PORT);
        addressPatternMap.put(IPV6_SIMPLE_BRACKET_PORT, P_IPV6_SIMPLE_BRACKET_PORT);

    }

    private String  address;
    private Integer port;
    private Integer type;

    //    private String  peerAddress;
//    private String  peerPort;
//    private Integer listenType;
//    private Integer pid;
//    private String  processName;
//    @Setter
//    private String  processFullCommand;

    private static String toOnlyRegex(String regex) {
        return String.format("^%s$", regex);
    }

    public static SessionAddress findMatch(String address) {
        return addressPatternMap
            .entrySet()
            .stream()
            .map(entry -> {
                PatternName patternName = entry.getKey();
                Pattern value = entry.getValue();
                Matcher matcher = value.matcher(address);
                return new FindPattern(patternName, matcher);
            })
            .filter(findPattern -> findPattern.matcher.find())
            .findAny()
            .map(findPattern -> {
                SessionAddressBuilder builder = SessionAddress.builder();
                Matcher matcher = findPattern.matcher;
                PatternName pattern = findPattern.patternName;
                switch (pattern) {
                    case IPV4_MAPPED_V6:
                    case IPV4_MAPPED_V6_BRACKET:
                    case IPV4_MAPPED_V6_PORT:
                    case IPV4_MAPPED_V6_BRACKET_PORT:
                        builder.address(matcher.group("ipv4"));
                        break;
                    default:
                        builder.address(matcher.group("address"));
                        break;
                }
                /* SET PORT */
                switch (pattern) {
                    case IPV4_PORT:
                    case IPV6_SIMPLE_BRACKET_PORT:
                    case IPV6_BRACKET_PORT:
                    case IPV4_MAPPED_V6_PORT:
                    case IPV4_MAPPED_V6_BRACKET_PORT:
                    case IPV4_LOOPBACK_PORT:
                        builder.port(matcher
                                         .group("port")
                                         .equals("*") ? 0 : Integer.parseInt(matcher.group("port")));
                        break;
                }
                /* SET Type */
                switch (pattern) {
                    case IPV4:
                    case IPV4_PORT:
                    case IPV4_LOOPBACK_PORT:
                        builder.type(0);
                    case IPV6:
                    case IPV6_SIMPLE:
                    case IPV4_MAPPED_V6_BRACKET:
                        builder.type(1);
                        break;
                    case IPV4_MAPPED_V6_BRACKET_PORT:
                    case IPV4_MAPPED_V6_PORT:
                    case IPV4_MAPPED_V6:
                        builder.type(2);
                        break;
                }

                return builder.build();
            })
            .orElseGet(() -> {
                log.debug("ADDRESS {} not matched", address);
                return null;
            });
    }

    public enum PatternName {
        IPV4,
        IPV6,
        IPV6_PORT,
        IPV6_BRACKET_PORT,
        IPV6_SIMPLE,
        IPV6_SIMPLE_BRACKET_PORT,
        IPV4_PORT,
        IPV4_MAPPED_V6,
        IPV4_MAPPED_V6_BRACKET,
        IPV4_MAPPED_V6_PORT,
        IPV4_MAPPED_V6_BRACKET_PORT,
        IPV4_LOOPBACK_PORT,
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class FindPattern {
        PatternName patternName;
        Matcher     matcher;
    }

    @Override
    public String toString() {
        return String.format("%s:%s", address, port);
    }
}

//\\[[^\\[]*\\]

//\\[::[f,F]{4}:(([0-9]|[1-9]0-9]|1[0-9][0-9]|2[0-4][0-9]|25[0-5])(\.(?!$)|$)){4}\\]