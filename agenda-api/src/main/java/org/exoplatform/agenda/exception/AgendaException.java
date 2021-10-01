// Copyright (C) 2020 eXo Platform SAS.
// SPDX-FileCopyrightText: 2021 eXo Platform SAS
//
// SPDX-License-Identifier: AGPL-3.0-only

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
