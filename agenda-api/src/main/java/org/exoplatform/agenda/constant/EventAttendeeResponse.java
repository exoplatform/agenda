// Copyright (C) 2020 eXo Platform SAS.
// SPDX-FileCopyrightText: 2021 eXo Platform SAS
//
// SPDX-License-Identifier: AGPL-3.0-only

package org.exoplatform.agenda.constant;

public enum EventAttendeeResponse {

  NEEDS_ACTION(EventAttendeeResponse.NEEDS_ACTION_VALUE),
  ACCEPTED(EventAttendeeResponse.ACCEPTED_VALUE),
  DECLINED(EventAttendeeResponse.DECLINED_VALUE),
  TENTATIVE(EventAttendeeResponse.TENTATIVE_VALUE);

  private static final String NEEDS_ACTION_VALUE = "NEEDS-ACTION";

  private static final String ACCEPTED_VALUE     = "ACCEPTED";

  private static final String DECLINED_VALUE     = "DECLINED";

  private static final String TENTATIVE_VALUE    = "TENTATIVE";

  private String              value;

  private EventAttendeeResponse(String value) {
    this.value = value;
  }

  public String getValue() {
    return value;
  }

  public static EventAttendeeResponse fromValue(String value) {
    switch (value) {
      case NEEDS_ACTION_VALUE:
        return NEEDS_ACTION;
      case ACCEPTED_VALUE:
        return ACCEPTED;
      case DECLINED_VALUE:
        return DECLINED;
      case TENTATIVE_VALUE:
        return TENTATIVE;
      default:
        return null;
    }
  }
}
