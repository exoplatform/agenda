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
package org.exoplatform.agenda.util;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * A share queued by {@link DecideContext#queueMerged(Object, Object, Consumer)}:
 * the first share of its key runs the effect on every share of the key the
 * context still holds once its transaction is over, the others run nothing.
 *
 * @param <S> the type of the shares
 * @param context the context the share was queued in
 * @param key what the shares are about
 * @param share this share
 * @param effect the effect of all the shares queued under the key
 */
record MergedShare<S>(DecideContext context, Object key, S share, Consumer<List<S>> effect) implements Runnable {

  @Override
  @SuppressWarnings("unchecked")
  public void run() {
    List<S> shares = new ArrayList<>();
    for (Runnable queued : context.getEffects()) {
      if (queued instanceof MergedShare<?> other && other.key.equals(key)) {
        if (shares.isEmpty() && other != this) {
          return;
        }
        shares.add((S) other.share);
      }
    }
    effect.accept(shares);
  }
}
