/**
 * Copyright (C) 2026 eXo Platform SAS.
 * <p>
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Affero General Public License
 * as published by the Free Software Foundation; either version 3
 * of the License, or (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, see<http://www.gnu.org/licenses/>.
 */
package org.exoplatform.agenda.rest;


import org.exoplatform.agenda.service.AgendaUserSettingsService;
import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("timezone")
@Tag(name = "timezone", description = "Update userTimeZone")
public class TimeZoneRest {

  private static final String USER_TIMEZONE = "UserTimeZone";

  @Autowired
  private AgendaUserSettingsService agendaUserSettingsService;


  @PostMapping
  @Secured("users")
  @Operation(summary = "Update user timeZone", method = "POST", description = "Update user timeZone")
  @ApiResponses(value = {
          @ApiResponse(responseCode = "200", description = "Request fulfilled"),
          @ApiResponse(responseCode = "404", description = "Not found"),
  })
  public void updateUserTimeZone(
          HttpServletRequest request,
          @RequestBody
          String timeZone) {
    try {
      agendaUserSettingsService.updateUserTimeZone(request.getRemoteUser(), timeZone);
    } catch (ObjectNotFoundException e) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
    }
  }
}
