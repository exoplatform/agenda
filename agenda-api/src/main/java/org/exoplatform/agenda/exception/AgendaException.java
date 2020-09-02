/*
 * Copyright (C) 2020 eXo Platform SAS.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Affero General Public License
 * as published by the Free Software Foundation; either version 3
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, see<http://www.gnu.org/licenses/>.
*/
package org.exoplatform.agenda.exception;

public class AgendaException extends Exception {

  private static final long         serialVersionUID = 3558967988412136652L;

  private final AgendaExceptionType agendaExceptionType;

  public AgendaException(AgendaExceptionType agendaExceptionType) {
    super(agendaExceptionType.getMessage());
    this.agendaExceptionType = agendaExceptionType;
  }

  public AgendaException(AgendaExceptionType agendaExceptionType, Exception e) {
    super(agendaExceptionType.getMessage(), e);
    this.agendaExceptionType = agendaExceptionType;
  }

  public AgendaExceptionType getAgendaExceptionType() {
    return agendaExceptionType;
  }
}
