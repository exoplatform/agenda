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
package org.exoplatform.agenda.rest.model;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * The platform user an e-mail address typed in the participants drawer
 * belongs to, as the drawer adds a participant: invited as themselves, never
 * as a guest from outside the platform (eXIP 7.3.0.21, US06).
 */
@Data
@NoArgsConstructor
public class AttendeeUserEntity {

  /** Technical identifier of the user's identity. */
  private String id;

  /** Provider of the identity: {@code organization}. */
  private String providerId;

  /** Username of the user. */
  private String remoteId;

  /** Full name of the user. */
  private String fullName;

  /** Avatar of the user. */
  private String avatarUrl;

}
