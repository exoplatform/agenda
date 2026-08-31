/**
 * Copyright (C) 2026 eXo Platform SAS.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.exoplatform.agenda.rest.util;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.exoplatform.agenda.constant.AvailabilityDisclosure;
import org.exoplatform.agenda.model.TimeBlock;
import org.exoplatform.agenda.model.UserBusyTime;
import org.exoplatform.agenda.rest.model.TimeBlockEntity;
import org.exoplatform.agenda.rest.model.UserBusyTimeEntity;

/**
 * Turns the busy-time domain model into what goes on the wire.
 * <p>
 * The one narrowing this class performs is the point of it: whatever the
 * service knows, only an identifier, a status and a list of two-instant ranges
 * leave the server.
 */
public class AvailabilityEntityBuilder {

  /**
   * Not instantiable: a builder of entities holds no state.
   */
  private AvailabilityEntityBuilder() {
  }

  /**
   * Builds the wire form of a whole busy-time answer.
   *
   * @param busyTimes what the service answered, one entry per participant
   * @param timeZone the zone the ranges are rendered in — the asking user's
   *          own, so the browser reads a block's wall-clock time the way it
   *          reads every other event on the same grid
   * @return one entity per entry, in the same order, never {@code null}
   */
  public static List<UserBusyTimeEntity> toEntities(List<UserBusyTime> busyTimes, ZoneId timeZone) {
    if (busyTimes == null) {
      return List.of();
    }
    return busyTimes.stream().map(busyTime -> toEntity(busyTime, timeZone)).toList();
  }

  /**
   * Builds the wire form of one participant's busy time.
   * <p>
   * The blocks are carried only when the status says a calendar was actually
   * read. For the other two statuses the field is left {@code null} rather
   * than emptied, so that no client can reach an empty array without having
   * gone past a status that says the list means nothing.
   *
   * @param busyTime one participant's busy time and read status
   * @param timeZone the zone the ranges are rendered in
   * @return the entity, never {@code null}
   */
  public static UserBusyTimeEntity toEntity(UserBusyTime busyTime, ZoneId timeZone) {
    boolean disclosed = busyTime.getDisclosure() == AvailabilityDisclosure.DISCLOSED;
    return new UserBusyTimeEntity(busyTime.getIdentityId(),
                                  busyTime.getDisclosure().getValue(),
                                  disclosed ? toBlockEntities(busyTime.getBusy(), timeZone) : null);
  }

  /**
   * Builds the wire form of a list of busy ranges.
   *
   * @param blocks the ranges, may be {@code null}
   * @param timeZone the zone the ranges are rendered in
   * @return one entity per range, never {@code null}
   */
  private static List<TimeBlockEntity> toBlockEntities(List<TimeBlock> blocks, ZoneId timeZone) {
    if (blocks == null) {
      return List.of();
    }
    return blocks.stream()
                 .map(block -> new TimeBlockEntity(format(block.getStart(), timeZone),
                                                   format(block.getEnd(), timeZone)))
                 .toList();
  }

  /**
   * Renders one bound in the asking user's own zone.
   * <p>
   * The zone is the <em>reader's</em>, never the busy person's. Free/busy is
   * computed on absolute instants here; rendering them in the viewer's zone is
   * how every other date on this grid arrives (the events REST takes the same
   * {@code timeZoneId}), and it also means a block never says anything about
   * where the person it belongs to happens to be.
   *
   * @param bound the instant to render
   * @param timeZone the zone to render it in
   * @return the ISO-8601 spelling with an offset, {@code null} when the bound
   *         is missing
   */
  private static String format(ZonedDateTime bound, ZoneId timeZone) {
    return bound == null ? null : DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(bound.withZoneSameInstant(timeZone));
  }

}
