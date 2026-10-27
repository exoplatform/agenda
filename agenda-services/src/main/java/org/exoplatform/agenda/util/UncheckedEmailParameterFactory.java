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

import net.fortuna.ical4j.model.ParameterFactory;
import net.fortuna.ical4j.model.parameter.XParameter;

/**
 * Reads the {@code EMAIL} parameter (RFC 7986) of an attendee or an organiser as
 * plain text. ical4j's own factory validates the value with commons-validator,
 * which the server does not ship (agenda's package carries ical4j alone): an
 * invitation carrying the parameter, as Apple Calendar writes it, would fail to
 * parse with a {@code NoClassDefFoundError}. The value is never used: an address
 * is read from the property's {@code mailto:} itself.
 */
public class UncheckedEmailParameterFactory implements ParameterFactory<XParameter> {

  /** The parameter's name. */
  public static final String EMAIL            = "EMAIL";

  private static final long  serialVersionUID = 1L;

  /**
   * The parameter, unchecked.
   *
   * @param value its value
   * @return the parameter
   */
  @Override
  public XParameter createParameter(String value) {
    return new XParameter(EMAIL, value);
  }

  /**
   * Whether a parameter is the one this factory reads.
   *
   * @param name the parameter's name
   * @return true for EMAIL, whatever its case
   */
  @Override
  public boolean supports(String name) {
    return EMAIL.equalsIgnoreCase(name);
  }
}
