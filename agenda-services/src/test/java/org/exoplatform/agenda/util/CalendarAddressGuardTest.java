/*
 * Copyright (C) 2026 eXo Platform SAS.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License
 * as published by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <gnu.org/licenses>.
 */
package org.exoplatform.agenda.util;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * Pins what a calendar link may point at (EXO-90278): the URL shapes refused,
 * every address range refused in IPv4 and IPv6 — the IPv4 forms carried inside
 * IPv6 included — the resolution rules, and the deployment's opt-out.
 */
class CalendarAddressGuardTest {

  private static final Set<Integer> PORTS = Set.of(80, 443, 8080, 8443);

  private final Map<String, InetAddress[]> dns = new HashMap<>();

  /**
   * A guard over the table of names, internal addresses refused.
   *
   * @return the guard
   */
  private CalendarAddressGuard guard() {
    return new CalendarAddressGuard(false, PORTS, this::resolve, Set.of());
  }

  /**
   * Resolves a name from the table.
   *
   * @param host the name
   * @return its addresses
   * @throws UnknownHostException when the table does not know it
   */
  private InetAddress[] resolve(String host) throws UnknownHostException {
    InetAddress[] addresses = dns.get(host);
    if (addresses == null) {
      throw new UnknownHostException(host);
    }
    return addresses;
  }

  /**
   * An address from its bytes, without the JDK turning an IPv4-mapped IPv6
   * address into an IPv4 one.
   *
   * @param bytes four or sixteen bytes
   * @return the address
   * @throws UnknownHostException never for a valid length
   */
  private static InetAddress address(int... bytes) throws UnknownHostException {
    byte[] raw = new byte[bytes.length];
    for (int i = 0; i < bytes.length; i++) {
      raw[i] = (byte) bytes[i];
    }
    if (raw.length == 16) {
      return java.net.Inet6Address.getByAddress(null, raw, null);
    }
    return InetAddress.getByAddress(raw);
  }

  /**
   * The reason a URL is refused.
   *
   * @param url the URL
   * @return the reason
   */
  private String refusal(String url) {
    return assertThrows(CalendarFeedException.class, () -> guard().normalize(url)).getReason();
  }

  /**
   * webcal and webcals are read as https, the fragment is dropped, and http and
   * https are kept as typed.
   *
   * @throws Exception never
   */
  @Test
  void webcalIsReadAsHttpsAndTheFragmentIsDropped() throws Exception {
    assertEquals("https://example.org/cal.ics", guard().normalize("webcal://example.org/cal.ics").toString());
    assertEquals("https://example.org/cal.ics", guard().normalize("  WEBCALS://example.org/cal.ics  ").toString());
    assertEquals("https://example.org/cal.ics?k=v", guard().normalize("https://example.org/cal.ics?k=v#frag").toString());
    assertEquals("http://example.org:8080/cal.ics", guard().normalize("HTTP://example.org:8080/cal.ics").toString());
  }

  /**
   * Only http and https are read.
   */
  @Test
  void schemesOtherThanHttpAreRefused() {
    assertEquals(CalendarFeedException.SCHEME_NOT_ALLOWED, refusal("ftp://example.org/cal.ics"));
    assertEquals(CalendarFeedException.SCHEME_NOT_ALLOWED, refusal("file:///etc/passwd"));
    assertEquals(CalendarFeedException.SCHEME_NOT_ALLOWED, refusal("javascript:alert(1)"));
    assertEquals(CalendarFeedException.SCHEME_NOT_ALLOWED, refusal("gopher://example.org/"));
  }

  /**
   * No user name or password travels in a link.
   */
  @Test
  void credentialsInTheUrlAreRefused() {
    assertEquals(CalendarFeedException.CREDENTIALS_IN_URL, refusal("https://user:secret@example.org/cal.ics"));
    assertEquals(CalendarFeedException.CREDENTIALS_IN_URL, refusal("https://user@example.org/cal.ics"));
  }

  /**
   * Only the allowed ports, the implicit ones included.
   */
  @Test
  void portsOutsideTheAllowedSetAreRefused() throws Exception {
    assertEquals(CalendarFeedException.PORT_NOT_ALLOWED, refusal("https://example.org:22/cal.ics"));
    assertEquals(CalendarFeedException.PORT_NOT_ALLOWED, refusal("http://example.org:6379/"));
    guard().normalize("https://example.org:8443/cal.ics");
    CalendarAddressGuard narrow = new CalendarAddressGuard(false, Set.of(8443), this::resolve, Set.of());
    assertEquals(CalendarFeedException.PORT_NOT_ALLOWED,
                 assertThrows(CalendarFeedException.class, () -> narrow.normalize("https://example.org/cal.ics")).getReason());
  }

  /**
   * What is not a usable URL is refused as such.
   */
  @Test
  void unusableUrlsAreRefused() {
    assertEquals(CalendarFeedException.INVALID_URL, refusal(null));
    assertEquals(CalendarFeedException.INVALID_URL, refusal("   "));
    assertEquals(CalendarFeedException.INVALID_URL, refusal("not a url"));
    assertEquals(CalendarFeedException.INVALID_URL, refusal("/relative/cal.ics"));
    assertEquals(CalendarFeedException.INVALID_URL, refusal("https:///cal.ics"));
    assertEquals(CalendarFeedException.INVALID_URL, refusal("https://example.org/a\nb"));
    assertEquals(CalendarFeedException.INVALID_URL, refusal("https://example.org/" + "a".repeat(CalendarAddressGuard.MAX_URL_LENGTH)));
  }

  /**
   * Every IPv4 range a calendar link must not reach is refused: this network,
   * RFC 1918, carrier-grade NAT, loopback, link-local with the metadata address,
   * IETF assignments, benchmarking, multicast, reserved and broadcast.
   *
   * @throws Exception never
   */
  @Test
  void everyInternalIpv4RangeIsRefused() throws Exception {
    int[][] refused = { { 0, 0, 0, 0 }, { 0, 1, 2, 3 }, { 10, 1, 2, 3 }, { 100, 64, 0, 1 }, { 100, 127, 255, 255 },
        { 127, 0, 0, 1 }, { 127, 5, 5, 5 }, { 169, 254, 169, 254 }, { 169, 254, 0, 1 }, { 172, 16, 0, 1 },
        { 172, 31, 255, 255 }, { 192, 168, 1, 1 }, { 192, 0, 0, 8 }, { 198, 18, 0, 1 }, { 198, 19, 255, 255 },
        { 224, 0, 0, 1 }, { 239, 1, 1, 1 }, { 240, 0, 0, 1 }, { 255, 255, 255, 255 } };
    for (int[] bytes : refused) {
      assertTrue(CalendarAddressGuard.isBlocked(address(bytes)), "refused: " + address(bytes));
    }
  }

  /**
   * The public neighbours of every refused IPv4 range stay reachable.
   *
   * @throws Exception never
   */
  @Test
  void publicIpv4NeighboursAreAllowed() throws Exception {
    int[][] allowed = { { 8, 8, 8, 8 }, { 1, 1, 1, 1 }, { 100, 63, 255, 255 }, { 100, 128, 0, 1 }, { 172, 15, 255, 255 },
        { 172, 32, 0, 1 }, { 192, 169, 0, 1 }, { 169, 253, 1, 1 }, { 192, 0, 1, 1 }, { 198, 20, 0, 1 },
        { 223, 255, 255, 254 }, { 93, 184, 216, 34 } };
    for (int[] bytes : allowed) {
      assertFalse(CalendarAddressGuard.isBlocked(address(bytes)), "allowed: " + address(bytes));
    }
  }

  /**
   * Every IPv6 range a calendar link must not reach is refused, and so is every
   * refused IPv4 address carried inside an IPv6 one: mapped, compatible, NAT64
   * and 6to4.
   *
   * @throws Exception never
   */
  @Test
  void everyInternalIpv6RangeAndEmbeddedIpv4IsRefused() throws Exception {
    InetAddress[] refused = { InetAddress.getByName("::"), InetAddress.getByName("::1"), InetAddress.getByName("fe80::1"),
        InetAddress.getByName("fec0::1"), InetAddress.getByName("fc00::1"), InetAddress.getByName("fd12:3456::1"),
        InetAddress.getByName("ff02::1"),
        // ::ffff:127.0.0.1 and ::ffff:10.0.0.1, IPv4-mapped
        address(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0xff, 0xff, 127, 0, 0, 1),
        address(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0xff, 0xff, 10, 0, 0, 1),
        // ::169.254.169.254, IPv4-compatible
        address(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 169, 254, 169, 254),
        // 64:ff9b::a9fe:a9fe, NAT64 of the metadata address
        address(0, 0x64, 0xff, 0x9b, 0, 0, 0, 0, 0, 0, 0, 0, 169, 254, 169, 254),
        // 2002:c0a8:0101::, 6to4 of 192.168.1.1
        address(0x20, 0x02, 192, 168, 1, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0) };
    for (InetAddress address : refused) {
      assertTrue(CalendarAddressGuard.isBlocked(address), "refused: " + address);
    }
  }

  /**
   * Public IPv6 addresses, and the IPv6 forms of public IPv4 ones, stay
   * reachable.
   *
   * @throws Exception never
   */
  @Test
  void publicIpv6AddressesAreAllowed() throws Exception {
    InetAddress[] allowed = { InetAddress.getByName("2001:4860:4860::8888"), InetAddress.getByName("2606:4700::1111"),
        address(0, 0x64, 0xff, 0x9b, 0, 0, 0, 0, 0, 0, 0, 0, 8, 8, 8, 8),
        address(0x20, 0x02, 8, 8, 8, 8, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0) };
    for (InetAddress address : allowed) {
      assertFalse(CalendarAddressGuard.isBlocked(address), "allowed: " + address);
    }
  }

  /**
   * A name is judged on every address it resolves to: one internal address among
   * public ones refuses it, and a refusal is told apart from a name resolving to
   * nothing.
   *
   * @throws Exception never
   */
  @Test
  void aNameIsRefusedWhenAnyAddressIsInternal() throws Exception {
    dns.put("public.test", new InetAddress[] { address(93, 184, 216, 34) });
    dns.put("private.test", new InetAddress[] { address(10, 0, 0, 5) });
    dns.put("mixed.test", new InetAddress[] { address(93, 184, 216, 34), address(127, 0, 0, 1) });
    dns.put("empty.test", new InetAddress[0]);

    assertArrayEquals(dns.get("public.test"), guard().resolveAllowed("public.test"));
    assertThrows(CalendarAddressGuard.RefusedAddressException.class, () -> guard().resolveAllowed("private.test"));
    assertThrows(CalendarAddressGuard.RefusedAddressException.class, () -> guard().resolveAllowed("mixed.test"));
    UnknownHostException unknown = assertThrows(UnknownHostException.class, () -> guard().resolveAllowed("unknown.test"));
    assertFalse(unknown instanceof CalendarAddressGuard.RefusedAddressException, "a name resolving to nothing is not a refusal");
    assertThrows(UnknownHostException.class, () -> guard().resolveAllowed("empty.test"));
  }

  /**
   * An IPv6 literal in brackets is judged like any address.
   *
   * @throws Exception never
   */
  @Test
  void anIpv6LiteralInBracketsIsJudged() throws Exception {
    CalendarAddressGuard jdk = new CalendarAddressGuard(false, PORTS);
    assertThrows(CalendarAddressGuard.RefusedAddressException.class, () -> jdk.resolveAllowed("[::1]"));
    assertThrows(CalendarAddressGuard.RefusedAddressException.class, () -> jdk.resolveAllowed("127.0.0.1"));
    assertThrows(CalendarAddressGuard.RefusedAddressException.class, () -> jdk.resolveAllowed("169.254.169.254"));
  }

  /**
   * The deployment's opt-out lets internal addresses through, and the test seam
   * exempts exactly the addresses it names.
   *
   * @throws Exception never
   */
  @Test
  void internalAddressesPassOnlyWhenTheDeploymentAllowsThem() throws Exception {
    dns.put("private.test", new InetAddress[] { address(10, 0, 0, 5) });
    dns.put("loopback.test", new InetAddress[] { address(127, 0, 0, 1) });
    dns.put("other-loopback.test", new InetAddress[] { address(127, 0, 0, 2) });

    CalendarAddressGuard allowing = new CalendarAddressGuard(true, PORTS, this::resolve, Set.of());
    assertArrayEquals(dns.get("private.test"), allowing.resolveAllowed("private.test"));

    CalendarAddressGuard exempting = new CalendarAddressGuard(false, PORTS, this::resolve, Set.of(address(127, 0, 0, 1)));
    assertArrayEquals(dns.get("loopback.test"), exempting.resolveAllowed("loopback.test"));
    assertThrows(CalendarAddressGuard.RefusedAddressException.class, () -> exempting.resolveAllowed("other-loopback.test"));
    assertThrows(CalendarAddressGuard.RefusedAddressException.class, () -> exempting.resolveAllowed("private.test"));
  }

}
