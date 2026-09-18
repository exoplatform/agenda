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
package org.exoplatform.agenda.model;

import java.util.List;

/**
 * Everything a delivery channel has to say about one calendar when the owner
 * opens the Share drawer (EXO-90385), read in one ask.
 *
 * <p>
 * <b>Why the two travel together.</b> Both answers come out of the same
 * conversation with the channel's server — the same collection resolved, the
 * same account asked — and a channel asked for them separately holds that
 * conversation twice. On a calendar hosted on a remote CalDAV server that cost
 * the drawer several seconds of pure latency, which is what this record
 * removes: the channel reads once and answers both.
 *
 * @param shares the access the channel's server holds that eXo does not record
 * @param meetingCopies whether the channel writes copies of the owner's eXo
 *          meetings into that calendar, which a share would expose
 */
public record ChannelShares(List<ExternalShare> shares, boolean meetingCopies) {
}
