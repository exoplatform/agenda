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
package org.exoplatform.agenda.service;

import java.util.List;
import java.util.Map;

/**
 * A patch of an event's fields, as asked.
 *
 * @param fields the fields and their values
 * @param updateAllOccurrences whether a change of a series reaches its dates
 *          individually modified
 * @param sendInvitations whether the attendees are notified
 * @param confirmClose whether the user confirmed a closing that confirms a
 *          waiting list
 */
record FieldsPatch(Map<String, List<String>> fields,
                   boolean updateAllOccurrences,
                   boolean sendInvitations,
                   boolean confirmClose) {
}
