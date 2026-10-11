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
package io.meeds.commons.http;

import java.net.InetAddress;
import java.util.Collection;

/**
 * The test seam of the fetch policy, reached from this add-on's tests: the
 * exemptions of {@link SafeFetchPolicyBuilder} are package-private, so that no
 * production policy can hold one, and a test reaches them from this package.
 * Test sources only; nothing here ships.
 */
public final class SafeFetchTestPolicies {

  /**
   * Not instantiated.
   */
  private SafeFetchTestPolicies() {
  }

  /**
   * Reads these addresses as public, whatever name they come from, so that a
   * stub on loopback answers while every other internal address stays refused.
   *
   * @param builder the policy being built
   * @param addresses the addresses read as public
   * @return the builder
   */
  public static SafeFetchPolicyBuilder exemptAddresses(SafeFetchPolicyBuilder builder, Collection<InetAddress> addresses) {
    return builder.exemptAddresses(addresses);
  }
}
