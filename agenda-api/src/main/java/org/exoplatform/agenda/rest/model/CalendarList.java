// Copyright (C) 2020 eXo Platform SAS.
// SPDX-FileCopyrightText: 2021 eXo Platform SAS
//
// SPDX-License-Identifier: AGPL-3.0-only

package org.exoplatform.agenda.rest.model;

import java.io.Serializable;
import java.util.List;

import lombok.*;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CalendarList implements Serializable {

  private static final long    serialVersionUID = -3532407964104047773L;

  private List<CalendarEntity> calendars;

  private int                  offset;

  private int                  limit;

  private int                  size;

}
