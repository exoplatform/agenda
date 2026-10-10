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

/**
 * The rows a decide step locks, and the event it acts on, checked under the
 * lock to still have the parent the caller resolved.
 *
 * @param lockedEventIds the event rows locked, an unmodifiable list
 * @param eventId the event acted on, or 0 when the locked rows are acted on as
 *          a whole
 * @param parentId the event's parent as resolved, 0 for none
 */
record DecideTarget(List<Long> lockedEventIds, long eventId, long parentId) {
}
